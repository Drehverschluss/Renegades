package com.drehverschluss.renegades.group;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.compat.minecolonies.MineColoniesCompat;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Spawns the members of a group template around a leader that is already in the world.
 */
public final class GroupSpawner {
    private static final int HORIZONTAL_RANGE = 4;
    private static final int VERTICAL_RANGE = 3;
    private static final int ATTEMPTS_PER_MEMBER = 12;

    private GroupSpawner() {
    }

    /**
     * Spawns as many members as fit (valid position, maxNearby cap); missing members are skipped, never the group.
     * Every member runs finalizeSpawn (so it rolls its own equipment) but no group of its own.
     *
     * @param natural a real natural spawn (not the spawngroup command): members then also avoid colonies
     *                (noSpawnInColonies)
     * @return number of spawned members
     */
    public static int spawnMembers(RenegadeEntity leader, SpawnGroupTemplate template, boolean natural) {
        if (!(leader.level() instanceof ServerLevel level)) {
            return 0;
        }

        RandomSource random = leader.getRandom();
        int capacity = RenegadeGroups.remainingCapacity(level, leader.position());
        UUID groupId = leader.getGroupId() != null ? leader.getGroupId() : UUID.randomUUID();
        int spawned = 0;

        for (SpawnGroupTemplate.Member member : template.members()) {
            int count = member.min() + random.nextInt(member.max() - member.min() + 1);
            for (int i = 0; i < count && capacity > 0; i++) {
                if (spawnMember(level, leader, member.entity(), groupId, random, natural)) {
                    spawned++;
                    capacity--;
                }
            }
        }

        if (spawned > 0) {
            leader.setGroupId(groupId);
        }
        Renegades.LOGGER.debug("Spawn group {} at {}: {} member(s) spawned", template.id(), leader.blockPosition(), spawned);
        return spawned;
    }

    private static boolean spawnMember(ServerLevel level, RenegadeEntity leader, EntityType<?> type, UUID groupId, RandomSource random, boolean natural) {
        BlockPos pos = findPosition(level, type, leader.blockPosition(), random);
        if (pos == null) {
            return false;
        }
        if (natural && MineColoniesCompat.blocksNaturalSpawn(level, pos)) {
            SpawnStats.recordMemberInColony();
            return false;
        }

        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return false;
        }

        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        if (mob instanceof RenegadeEntity renegade) {
            // Set before finalizeSpawn so the member does not roll a group of its own.
            renegade.setGroupId(groupId);
        }
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
        if (mob.isSpawnCancelled()) {
            mob.discard();
            return false;
        }
        level.addFreshEntityWithPassengers(mob);
        return true;
    }

    /**
     * A free spot on solid ground near the leader that passes the spawn placement rules of the member's type
     * (no fluids, not inside blocks, solid block below).
     */
    @Nullable
    private static BlockPos findPosition(ServerLevel level, EntityType<?> type, BlockPos origin, RandomSource random) {
        for (int attempt = 0; attempt < ATTEMPTS_PER_MEMBER; attempt++) {
            int x = origin.getX() + random.nextInt(HORIZONTAL_RANGE * 2 + 1) - HORIZONTAL_RANGE;
            int z = origin.getZ() + random.nextInt(HORIZONTAL_RANGE * 2 + 1) - HORIZONTAL_RANGE;
            for (int dy = 0; dy <= VERTICAL_RANGE * 2; dy++) {
                // 0, +1, -1, +2, -2, ...
                int offset = (dy + 1) / 2 * (dy % 2 == 0 ? -1 : 1);
                BlockPos pos = new BlockPos(x, origin.getY() + offset, z);
                if (SpawnPlacements.isSpawnPositionOk(type, level, pos)
                        && SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, random)
                        && level.noCollision(type.getSpawnAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D))) {
                    return pos;
                }
            }
        }
        return null;
    }
}
