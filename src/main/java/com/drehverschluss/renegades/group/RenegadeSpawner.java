package com.drehverschluss.renegades.group;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.compat.minecolonies.MineColoniesCompat;
import com.drehverschluss.renegades.config.RenegadesConfig;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Renegades' own spawn timer: every spawnIntervalSeconds each player gets one attempt that succeeds with spawnChance.
 * Independent of the vanilla monster spawning and its mob cap, so the rate stays predictable in packs with many mobs.
 */
public final class RenegadeSpawner {
    private static final int POSITION_ATTEMPTS = 10;
    private static final int UNDERGROUND_RANGE = 32;

    private RenegadeSpawner() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int interval = RenegadesConfig.SPAWN_INTERVAL_SECONDS.get() * 20;
        int tick = server.getTickCount();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Per-player offset, so players do not all check in the same tick.
            if (Math.floorMod(tick + player.getUUID().hashCode(), interval) == 0) {
                attempt(player);
            }
        }
    }

    private static void attempt(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (player.isSpectator()
                || level.getDifficulty() == Difficulty.PEACEFUL
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                || !level.getServer().isSpawningMonsters()) {
            return;
        }

        RandomSource random = level.getRandom();
        if (random.nextDouble() >= RenegadesConfig.SPAWN_CHANCE.get()) {
            SpawnStats.recordAttempt(SpawnStats.Result.FAILED_CHANCE);
            return;
        }
        if (RenegadeGroups.remainingCapacity(level, player.position()) <= 0) {
            SpawnStats.recordAttempt(SpawnStats.Result.CAPPED);
            return;
        }

        boolean anyBiomeAllowed = false;
        boolean anyBiomeRejected = false;
        boolean anyInColony = false;

        for (int i = 0; i < POSITION_ATTEMPTS; i++) {
            BlockPos pos = findCandidate(level, player, random);
            if (pos == null) {
                continue;
            }
            if (MineColoniesCompat.blocksNaturalSpawn(level, pos)) {
                anyInColony = true;
                continue;
            }

            SpawnGroupTemplate template = SpawnGroups.pickForPosition(level, pos, random);
            if (template == null) {
                anyBiomeRejected = true;
                continue;
            }
            anyBiomeAllowed = true;

            if (!fitsType(level, template.leader(), pos, random)) {
                continue;
            }
            if (RenegadeGroups.remainingCapacity(level, Vec3.atBottomCenterOf(pos)) <= 0) {
                SpawnStats.recordAttempt(SpawnStats.Result.CAPPED);
                return;
            }
            if (spawnLeader(level, template.leader(), pos, random)) {
                SpawnStats.recordAttempt(SpawnStats.Result.SPAWNED);
                Renegades.LOGGER.debug("Spawn timer: {} at {} near {}", template.leader().getDescriptionId(), pos, player.getName().getString());
                return;
            }
        }

        SpawnStats.recordAttempt(anyInColony ? SpawnStats.Result.IN_COLONY
                : anyBiomeRejected && !anyBiomeAllowed ? SpawnStats.Result.NO_BIOME : SpawnStats.Result.NO_POSITION);
    }

    /**
     * A free 1x2 spot with solid ground minDistance..maxDistance blocks around the player: on the surface, or (with
     * allowUnderground, half of the time, and always in dimensions with a ceiling) in a cave within 32 blocks of the
     * player's height.
     */
    @Nullable
    private static BlockPos findCandidate(ServerLevel level, ServerPlayer player, RandomSource random) {
        int minDistance = RenegadesConfig.MIN_DISTANCE.get();
        int maxDistance = Math.max(minDistance, RenegadesConfig.MAX_DISTANCE.get());
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);

        BlockPos column = new BlockPos(x, player.getBlockY(), z);
        if (!level.isPositionEntityTicking(column)) {
            return null;
        }

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        boolean underground = level.dimensionType().hasCeiling()
                || RenegadesConfig.ALLOW_UNDERGROUND.get() && random.nextBoolean();
        if (!underground) {
            BlockPos pos = new BlockPos(x, surfaceY, z);
            return isFreeSpot(level, pos) ? pos : null;
        }

        int low = Math.max(level.getMinBuildHeight() + 1, player.getBlockY() - UNDERGROUND_RANGE);
        int high = Math.min(surfaceY - 1, player.getBlockY() + UNDERGROUND_RANGE);
        if (low > high) {
            return null;
        }
        for (int y = low + random.nextInt(high - low + 1); y >= low; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (isFreeSpot(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isFreeSpot(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && isEmpty(level, pos)
                && isEmpty(level, pos.above());
    }

    private static boolean isEmpty(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    /**
     * The same checks a natural spawn of this type would get: placement (ground, no fluids), spawn rules (Peaceful,
     * darkness with requireDarkness) and no collision with blocks.
     */
    private static boolean fitsType(ServerLevel level, EntityType<?> type, BlockPos pos, RandomSource random) {
        return SpawnPlacements.isSpawnPositionOk(type, level, pos)
                && SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, random)
                && level.noCollision(type.getSpawnAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
    }

    /**
     * Spawns through the regular NeoForge path (position check event, FinalizeSpawnEvent, finalizeSpawn with NATURAL),
     * so level mods and persistence work like for any natural spawn. The leader rolls its group in finalizeSpawn.
     */
    private static boolean spawnLeader(ServerLevel level, EntityType<?> type, BlockPos pos, RandomSource random) {
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return false;
        }

        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        if (!EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL)) {
            mob.discard();
            return false;
        }
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
        if (mob.isSpawnCancelled()) {
            mob.discard();
            return false;
        }
        level.addFreshEntityWithPassengers(mob);
        return true;
    }
}
