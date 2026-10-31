package com.drehverschluss.renegades.command;

import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.drehverschluss.renegades.equipment.EquipmentPoolEntry;
import com.drehverschluss.renegades.equipment.EquipmentPools;
import com.drehverschluss.renegades.equipment.EquipmentSelector;
import com.drehverschluss.renegades.group.RenegadeGroups;
import com.drehverschluss.renegades.group.SpawnGroupTemplate;
import com.drehverschluss.renegades.group.SpawnGroups;
import com.drehverschluss.renegades.group.SpawnStats;
import com.mojang.brigadier.CommandDispatcher;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Debug commands (permission level 2): /renegades pools|reequip|groups|spawngroup|nearby.
 */
public final class RenegadesCommand {
    private RenegadesCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("renegades")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("pools")
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        EquipmentPools.entityTypes().stream().map(BuiltInRegistries.ENTITY_TYPE::getKey), builder))
                                .executes(context -> listPools(context.getSource(), ResourceLocationArgument.getId(context, "entity")))))
                .then(Commands.literal("reequip")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> reequip(context.getSource(), EntityArgument.getEntities(context, "targets")))))
                .then(Commands.literal("groups")
                        .then(Commands.argument("leader", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        SpawnGroups.all().stream().map(template -> BuiltInRegistries.ENTITY_TYPE.getKey(template.leader())).distinct(), builder))
                                .executes(context -> listGroups(context.getSource(), ResourceLocationArgument.getId(context, "leader")))))
                .then(Commands.literal("spawngroup")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        SpawnGroups.all().stream().map(SpawnGroupTemplate::id), builder))
                                .executes(context -> spawnGroup(context.getSource(), ResourceLocationArgument.getId(context, "id")))))
                .then(Commands.literal("nearby")
                        .executes(context -> listNearby(context.getSource())))
                .then(Commands.literal("stats")
                        .executes(context -> showStats(context.getSource()))
                        .then(Commands.literal("reset")
                                .executes(context -> resetStats(context.getSource())))));
    }

    private static int showStats(CommandSourceStack source) {
        SpawnStats.Snapshot stats = SpawnStats.snapshot(source.getServer().getTickCount());
        long spawned = stats.leaders() + stats.members();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Renegade spawn timer over %.1f min (%d ticks): %d attempts (%.1f per hour)",
                stats.ticks() / 1200.0D, stats.ticks(), stats.attempts(), stats.perHour(stats.attempts()))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Failed: chance %d, no valid position %d, no matching biome %d, cap %d",
                stats.failedChance(), stats.noPosition(), stats.noBiome(), stats.capped())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Spawned: %d (%d leaders + %d group members) = %.1f per hour",
                spawned, stats.leaders(), stats.members(), stats.perHour(spawned))), false);
        return (int) spawned;
    }

    private static int resetStats(CommandSourceStack source) {
        SpawnStats.reset(source.getServer().getTickCount());
        source.sendSuccess(() -> Component.literal("Spawn statistics reset"), true);
        return 1;
    }

    private static int listGroups(CommandSourceStack source, ResourceLocation leaderId) {
        List<SpawnGroupTemplate> templates = SpawnGroups.all().stream()
                .filter(template -> BuiltInRegistries.ENTITY_TYPE.getKey(template.leader()).equals(leaderId))
                .toList();
        source.sendSuccess(() -> Component.literal(leaderId + ": " + templates.size() + " spawn group template(s)"), false);

        for (SpawnGroupTemplate template : templates) {
            String members = template.members().isEmpty() ? "none (alone)" : template.members().stream()
                    .map(member -> BuiltInRegistries.ENTITY_TYPE.getKey(member.entity()) + " x" + member.min()
                            + (member.max() != member.min() ? ".." + member.max() : ""))
                    .collect(Collectors.joining(", "));
            source.sendSuccess(() -> Component.literal("- " + template.id() + " | weight " + template.weight()
                    + " | " + template.conditions().describe() + " | members: " + members), false);
        }
        return templates.size();
    }

    private static int spawnGroup(CommandSourceStack source, ResourceLocation id) {
        Optional<SpawnGroupTemplate> template = SpawnGroups.find(id);
        if (template.isEmpty()) {
            source.sendFailure(Component.literal("Unknown spawn group " + id));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        if (RenegadeGroups.remainingCapacity(level, pos) <= 0) {
            source.sendFailure(Component.literal("maxNearby (" + RenegadesConfig.MAX_NEARBY.get() + ") already reached within "
                    + RenegadesConfig.CAP_RADIUS.get() + " blocks of this position or the nearest player"));
            return 0;
        }
        if (!(template.get().leader().create(level) instanceof RenegadeEntity leader)) {
            source.sendFailure(Component.literal("Leader of " + template.get().id() + " is not a renegade"));
            return 0;
        }

        // Like a natural spawn (spawn type NATURAL, so it is persistent), but with this template instead of a random one.
        leader.moveTo(pos.x, pos.y, pos.z, source.getRotation().y, 0.0F);
        leader.setPendingGroup(template.get());
        EventHooks.finalizeMobSpawn(leader, level, level.getCurrentDifficultyAt(leader.blockPosition()), MobSpawnType.NATURAL, null);
        level.addFreshEntityWithPassengers(leader);
        int members = leader.spawnPendingGroup();

        source.sendSuccess(() -> Component.literal("Spawned " + template.get().id() + ": leader + " + members + " member(s)"
                + (leader.getGroupId() != null ? ", group " + shortId(leader.getGroupId()) : "")), true);
        return 1 + members;
    }

    private static int listNearby(CommandSourceStack source) {
        int radius = RenegadesConfig.CAP_RADIUS.get();
        Vec3 pos = source.getPosition();
        List<RenegadeEntity> renegades = source.getLevel().getEntitiesOfClass(RenegadeEntity.class, new AABB(BlockPos.containing(pos)).inflate(radius),
                entity -> entity.isAlive() && entity.distanceToSqr(pos) <= (double) radius * radius);

        source.sendSuccess(() -> Component.literal(renegades.size() + " renegade(s) within " + radius + " blocks (maxNearby "
                + RenegadesConfig.MAX_NEARBY.get() + ")"), false);
        for (RenegadeEntity renegade : renegades) {
            String origin = renegade.getSpawnType() == null ? "unknown" : renegade.getSpawnType().name().toLowerCase(Locale.ROOT);
            boolean persistent = renegade.isPersistenceRequired() || renegade.requiresCustomPersistence();
            source.sendSuccess(() -> Component.literal("- " + renegade.getName().getString()
                    + " at " + renegade.blockPosition().toShortString()
                    + " (" + Math.round(Math.sqrt(renegade.distanceToSqr(pos))) + " blocks)"
                    + " | group " + (renegade.getGroupId() == null ? "none" : shortId(renegade.getGroupId()))
                    + " | spawn " + origin
                    + " | persistent " + persistent
                    + String.format(Locale.ROOT, " | HP %.1f/%.1f | attack %.1f", renegade.getHealth(), renegade.getMaxHealth(),
                            renegade.getAttributeValue(Attributes.ATTACK_DAMAGE))
                    + (renegade.isInvisible() ? " | invisible" : "")
                    + (renegade.getCombatBrain() == null ? "" : " | " + renegade.getCombatBrain().describe())), false);
        }
        return renegades.size();
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static int listPools(CommandSourceStack source, ResourceLocation entityId) {
        Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId);
        if (entityType.isEmpty()) {
            source.sendFailure(Component.literal("Unknown entity " + entityId));
            return 0;
        }

        EquipmentPools.Pool pool = EquipmentPools.get(entityType.get());
        source.sendSuccess(() -> Component.literal(entityId + ": " + pool.entries().size() + " entries (" + pool.fallbackCount()
                + " fallback) from " + pool.files().size() + " file(s)"), false);

        for (EquipmentPoolEntry entry : pool.entries()) {
            String slots = entry.equipment().entrySet().stream()
                    .map(slot -> slot.getKey().getName() + "=" + slot.getValue())
                    .collect(Collectors.joining(", "));
            source.sendSuccess(() -> Component.literal("- " + entry.id() + (entry.fallback() ? " [fallback]" : "")
                    + " | weight " + entry.weight() + " | " + entry.conditions().describe()
                    + (entry.variant() == null ? "" : " | variant " + entry.variant())
                    + " | " + slots + " | file " + entry.source()), false);
        }
        return pool.entries().size();
    }

    private static int reequip(CommandSourceStack source, Collection<? extends Entity> targets) {
        int count = 0;

        for (Entity target : targets) {
            if (!(target instanceof RenegadeEntity renegade) || !(target.level() instanceof ServerLevel level)) {
                continue;
            }
            count++;

            String name = target.getName().getString();
            Optional<EquipmentSelector.Selection> selection = EquipmentSelector.reequip(renegade, level);
            if (selection.isEmpty()) {
                String context = EquipmentSelector.currentContext(renegade, level).describe();
                source.sendSuccess(() -> Component.literal(name + ": no entry matches, equipment cleared | " + context), false);
                continue;
            }

            EquipmentSelector.Selection chosen = selection.get();
            source.sendSuccess(() -> Component.literal(name + ": '" + chosen.entry().id() + "'"
                    + (chosen.entry().fallback() ? " [fallback]" : "")
                    + " (1 of " + chosen.candidates() + " matching) from " + chosen.entry().source()
                    + " | " + chosen.context().describe()
                    + " | entry conditions: " + chosen.entry().conditions().describe()), false);
        }

        if (count == 0) {
            source.sendFailure(Component.literal("No renegades among the targets"));
        }
        return count;
    }
}
