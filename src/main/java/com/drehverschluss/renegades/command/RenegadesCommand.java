package com.drehverschluss.renegades.command;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.combat.CombatProfiler;
import com.drehverschluss.renegades.compat.apotheosis.ApotheosisCompat;
import com.drehverschluss.renegades.compat.spellengine.SpellEngineCompat;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.drehverschluss.renegades.equipment.EquipmentPoolEntry;
import com.drehverschluss.renegades.equipment.EquipmentPools;
import com.drehverschluss.renegades.equipment.EquipmentSelector;
import com.drehverschluss.renegades.equipment.RareItemEntry;
import com.drehverschluss.renegades.equipment.RareItems;
import com.drehverschluss.renegades.group.RenegadeGroups;
import com.drehverschluss.renegades.group.SpawnGroupTemplate;
import com.drehverschluss.renegades.group.SpawnGroups;
import com.drehverschluss.renegades.group.SpawnStats;
import com.drehverschluss.renegades.registry.ModEntityTypes;
import com.drehverschluss.renegades.visual.CasterVisualEntry;
import com.drehverschluss.renegades.visual.CasterVisuals;
import com.drehverschluss.renegades.visual.VisualRef;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Debug commands (permission level 2): /renegades pools|reequip|groups|spawngroup|nearby|damagetags|stats|profile|rareitems|castervisuals|spawnelite.
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
                .then(Commands.literal("damagetags")
                        .executes(context -> showDamageTags(context.getSource())))
                .then(Commands.literal("profile")
                        .then(Commands.literal("start")
                                .executes(context -> {
                                    CombatProfiler.begin(context.getSource().getServer().getTickCount());
                                    context.getSource().sendSuccess(() -> Component.literal("Renegade AI profiling started"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("stop")
                                .executes(context -> {
                                    String report = CombatProfiler.finish(context.getSource().getServer().getTickCount());
                                    context.getSource().sendSuccess(() -> Component.literal(report), false);
                                    return 1;
                                })))
                .then(Commands.literal("stats")
                        .executes(context -> showStats(context.getSource()))
                        .then(Commands.literal("reset")
                                .executes(context -> resetStats(context.getSource()))))
                .then(Commands.literal("rareitems")
                        .executes(context -> listRareItems(context.getSource())))
                .then(Commands.literal("castervisuals")
                        .executes(context -> listCasterVisuals(context.getSource())))
                .then(Commands.literal("spawnelite")
                        .then(Commands.argument("class", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        renegadeTypes().stream().map(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath()), builder))
                                .then(Commands.argument("rarity", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(ApotheosisCompat.rarityIds(), builder))
                                        .executes(context -> spawnElite(context.getSource(), StringArgumentType.getString(context, "class"),
                                                ResourceLocationArgument.getId(context, "rarity"), false))
                                        .then(Commands.literal("set")
                                                .executes(context -> spawnElite(context.getSource(), StringArgumentType.getString(context, "class"),
                                                        ResourceLocationArgument.getId(context, "rarity"), true)))))));
    }

    /**
     * The loaded caster_visuals per class: which spell lends its look to which ability (per variant) and whether that
     * spell exists, plus the casterVisuals config value.
     */
    private static int listCasterVisuals(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        boolean spellEngine = SpellEngineCompat.isLoaded();
        source.sendSuccess(() -> Component.literal("casterVisuals = " + RenegadesConfig.CASTER_VISUALS.get() + ", Spell Engine "
                + (spellEngine ? "installed" : "not installed") + " -> " + (CasterVisuals.useSpellLook() ? "spell looks where assigned" : "built-in look only")), false);
        int count = 0;
        for (Map.Entry<EntityType<?>, Map<String, CasterVisualEntry>> type : CasterVisuals.all().entrySet()) {
            source.sendSuccess(() -> Component.literal(BuiltInRegistries.ENTITY_TYPE.getKey(type.getKey()) + ": " + type.getValue().size() + " abilities"), false);
            for (Map.Entry<String, CasterVisualEntry> ability : type.getValue().entrySet()) {
                count++;
                CasterVisualEntry entry = ability.getValue();
                List<String> parts = new ArrayList<>();
                if (entry.base() != null && entry.base().spell() != null) {
                    parts.add(describeSpell(level, entry.forVariant(null)));
                }
                entry.variants().keySet().stream().sorted().forEach(variant -> parts.add(variant + "=" + describeSpell(level, entry.forVariant(variant))));
                source.sendSuccess(() -> Component.literal("- " + ability.getKey() + ": " + String.join(", ", parts)), false);
            }
        }
        if (count == 0) {
            source.sendSuccess(() -> Component.literal("No spell looks assigned (data/<namespace>/renegades/caster_visuals)"), false);
        }
        return count;
    }

    private static String describeSpell(ServerLevel level, @Nullable VisualRef ref) {
        if (ref == null) {
            return "-";
        }
        List<String> off = new ArrayList<>();
        if (!ref.model()) {
            off.add("model");
        }
        if (!ref.particles()) {
            off.add("particles");
        }
        if (!ref.sounds()) {
            off.add("sounds");
        }
        return ref.spell() + (SpellEngineCompat.spellExists(level, ref.spell()) ? " (found)" : " (MISSING)")
                + (off.isEmpty() ? "" : " [built-in " + String.join("/", off) + "]");
    }

    private static List<EntityType<? extends RenegadeEntity>> renegadeTypes() {
        return List.of(ModEntityTypes.WARRIOR.get(), ModEntityTypes.ROGUE.get(), ModEntityTypes.PALADIN.get(),
                ModEntityTypes.ARCHER.get(), ModEntityTypes.WIZARD.get(), ModEntityTypes.PRIEST.get());
    }

    /**
     * The loaded rare items per class: slot, chance (and with rareItemChanceMultiplier), items, replaces, conditions.
     */
    private static int listRareItems(CommandSourceStack source) {
        double multiplier = RenegadesConfig.RARE_ITEM_CHANCE_MULTIPLIER.get();
        int total = 0;
        for (EntityType<?> type : renegadeTypes()) {
            List<RareItemEntry> entries = RareItems.get(type);
            total += entries.size();
            source.sendSuccess(() -> Component.literal(BuiltInRegistries.ENTITY_TYPE.getKey(type) + ": " + entries.size() + " rare item entries"
                    + " (rareItemChanceMultiplier " + multiplier + ")"), false);
            for (RareItemEntry entry : entries) {
                double effective = Math.min(1.0D, entry.chance() * multiplier);
                source.sendSuccess(() -> Component.literal("- " + entry.id() + " | " + entry.slot().getName()
                        + String.format(Locale.ROOT, " | chance %.5f (1 in %s)", effective, effective > 0.0D ? String.valueOf(Math.round(1.0D / effective)) : "never")
                        + " | " + entry.items().options().size() + " item option(s): " + entry.items()
                        + " | replaces " + (entry.replaces() == null ? "any piece" : entry.replaces())
                        + " | " + entry.conditions().describe()
                        + (entry.allowAffixes() ? "" : " | no affixes")), false);
            }
        }
        return total;
    }

    /**
     * Spawns a renegade like a spawn egg and makes it an elite with the given rarity: one own piece, or with "set" all
     * pieces that can take affixes (Apotheosis only).
     */
    private static int spawnElite(CommandSourceStack source, String className, ResourceLocation rarity, boolean fullSet) {
        if (!ApotheosisCompat.isLoaded()) {
            source.sendFailure(Component.literal("Apotheosis is not installed, there are no elite renegades"));
            return 0;
        }
        Optional<EntityType<? extends RenegadeEntity>> type = renegadeTypes().stream()
                .filter(candidate -> BuiltInRegistries.ENTITY_TYPE.getKey(candidate).getPath().equals(className))
                .findFirst();
        if (type.isEmpty()) {
            source.sendFailure(Component.literal("Unknown renegade class " + className));
            return 0;
        }
        if (ApotheosisCompat.rarityColor(rarity).isEmpty()) {
            source.sendFailure(Component.literal("Unknown rarity " + rarity + " (known: " + ApotheosisCompat.rarityIds() + ")"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        RenegadeEntity renegade = type.get().create(level);
        if (renegade == null) {
            return 0;
        }
        Vec3 pos = source.getPosition();
        renegade.moveTo(pos.x, pos.y, pos.z, source.getRotation().y, 0.0F);
        renegade.setForcedElite(new ApotheosisCompat.ForcedElite(rarity, fullSet));
        EventHooks.finalizeMobSpawn(renegade, level, level.getCurrentDifficultyAt(renegade.blockPosition()), MobSpawnType.SPAWN_EGG, null);
        level.addFreshEntityWithPassengers(renegade);

        ApotheosisCompat.EliteResult result = renegade.getLastEliteResult();
        if (result.upgraded().isEmpty()) {
            source.sendFailure(Component.literal("Spawned " + renegade.getName().getString() + ", but no piece could take affixes (or no player nearby)"));
            return 0;
        }
        String pieces = result.upgraded().stream()
                .map(piece -> piece.slot().getName() + "=" + piece.item() + " (" + piece.rarity() + ", " + piece.affixes() + " affixes)")
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal("Spawned ").append(renegade.getDisplayName())
                .append(Component.literal(": " + pieces)), true);
        return result.upgraded().size();
    }

    private static int showStats(CommandSourceStack source) {
        SpawnStats.Snapshot stats = SpawnStats.snapshot(source.getServer().getTickCount());
        long spawned = stats.leaders() + stats.members();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Renegade spawn timer over %.1f min (%d ticks): %d attempts (%.1f per hour)",
                stats.ticks() / 1200.0D, stats.ticks(), stats.attempts(), stats.perHour(stats.attempts()))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Failed: chance %d, no valid position %d, no matching biome %d, cap %d, in colony %d (group members left out in colony: %d)",
                stats.failedChance(), stats.noPosition(), stats.noBiome(), stats.capped(), stats.inColony(), stats.membersInColony())), false);
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

    /**
     * For our spell damage types (and Spell Power's, if installed): the damage type tags that decide how other mods
     * treat them (magic and lightning reductions, armor and shield bypass, witch resistance).
     */
    private static int showDamageTags(CommandSourceStack source) {
        List<TagKey<DamageType>> tags = List.of(
                TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("c", "is_magic")),
                TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("neoforge", "is_magic")),
                DamageTypeTags.IS_LIGHTNING, DamageTypeTags.IS_PROJECTILE, DamageTypeTags.BYPASSES_ARMOR, DamageTypeTags.BYPASSES_SHIELD,
                DamageTypeTags.WITCH_RESISTANT_TO);
        Registry<DamageType> registry = source.getServer().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        List<Holder.Reference<DamageType>> types = registry.holders()
                .filter(holder -> holder.key().location().getNamespace().equals(Renegades.MODID)
                        || holder.key().location().getNamespace().equals("spell_power"))
                .sorted(Comparator.comparing(holder -> holder.key().location().toString()))
                .toList();
        for (Holder.Reference<DamageType> type : types) {
            String in = tags.stream().filter(type::is).map(tag -> tag.location().toString()).collect(Collectors.joining(", "));
            source.sendSuccess(() -> Component.literal(type.key().location() + ": " + (in.isEmpty() ? "-" : in)), false);
        }
        return types.size();
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
