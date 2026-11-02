package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.registry.ModEntityTypes;
import com.drehverschluss.renegades.registry.ModItems;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/**
 * All renegade classes in one table: entity type, spawn egg and the mods a class needs. A class whose mods are missing
 * stays registered (worlds and the client registry stay the same), but is not available: no natural spawns, spawn groups
 * with it are left out, its egg is not in the creative tab and eggs and commands report the missing mods instead of
 * spawning it. A saved renegade of such a class loads with the fallback gear of its pools (see RenegadeEntity).
 */
public final class RenegadeClasses {
    /**
     * @param requiredMods          mod ids the class needs (its gear, spells and effects come from them)
     * @param alwaysSpellEngineLook whether its spells always take the look from caster_visuals, also with
     *                              casterVisuals = "builtin" (classes of mods that need Spell Engine have no own look)
     */
    public record RenegadeClass(Supplier<? extends EntityType<? extends RenegadeEntity>> type, Supplier<? extends Item> spawnEgg,
                                List<String> requiredMods, boolean alwaysSpellEngineLook) {
        public EntityType<? extends RenegadeEntity> entityType() {
            return this.type.get();
        }

        public boolean isAvailable() {
            return this.requiredMods.stream().allMatch(ModList.get()::isLoaded);
        }

        public List<String> missingMods() {
            return this.requiredMods.stream().filter(mod -> !ModList.get().isLoaded(mod)).toList();
        }
    }

    private static final List<RenegadeClass> ALL = List.of(
            builtIn(ModEntityTypes.WARRIOR, ModItems.WARRIOR_SPAWN_EGG),
            builtIn(ModEntityTypes.ROGUE, ModItems.ROGUE_SPAWN_EGG),
            builtIn(ModEntityTypes.PALADIN, ModItems.PALADIN_SPAWN_EGG),
            builtIn(ModEntityTypes.ARCHER, ModItems.ARCHER_SPAWN_EGG),
            builtIn(ModEntityTypes.WIZARD, ModItems.WIZARD_SPAWN_EGG),
            builtIn(ModEntityTypes.PRIEST, ModItems.PRIEST_SPAWN_EGG),
            new RenegadeClass(ModEntityTypes.ELEMENTAL_WIZARD, ModItems.ELEMENTAL_WIZARD_SPAWN_EGG, List.of("elemental_wizards_rpg"), true),
            new RenegadeClass(ModEntityTypes.BERSERKER, ModItems.BERSERKER_SPAWN_EGG, List.of("berserker_rpg"), true),
            new RenegadeClass(ModEntityTypes.BARD, ModItems.BARD_SPAWN_EGG, List.of("bards_rpg"), true));

    private RenegadeClasses() {
    }

    private static RenegadeClass builtIn(Supplier<? extends EntityType<? extends RenegadeEntity>> type, Supplier<? extends Item> egg) {
        return new RenegadeClass(type, egg, List.of(), false);
    }

    public static List<RenegadeClass> all() {
        return ALL;
    }

    /**
     * The entity types of the classes that are available right now.
     */
    public static List<EntityType<? extends RenegadeEntity>> availableTypes() {
        return ALL.stream().filter(RenegadeClass::isAvailable).<EntityType<? extends RenegadeEntity>>map(RenegadeClass::entityType).toList();
    }

    @Nullable
    public static RenegadeClass of(EntityType<?> type) {
        for (RenegadeClass renegadeClass : ALL) {
            if (renegadeClass.entityType() == type) {
                return renegadeClass;
            }
        }
        return null;
    }

    /**
     * Whether the type may be spawned; true for everything that is not a renegade class (other mods' mobs in spawn
     * groups are not checked here).
     */
    public static boolean isAvailable(EntityType<?> type) {
        RenegadeClass renegadeClass = of(type);
        return renegadeClass == null || renegadeClass.isAvailable();
    }

    public static boolean alwaysSpellEngineLook(EntityType<?> type) {
        RenegadeClass renegadeClass = of(type);
        return renegadeClass != null && renegadeClass.alwaysSpellEngineLook();
    }

    /**
     * "Elemental Wizard Renegade is not available (requires: elemental_wizards_rpg)".
     */
    public static Component unavailableMessage(EntityType<?> type) {
        RenegadeClass renegadeClass = of(type);
        String missing = renegadeClass == null ? "" : String.join(", ", renegadeClass.missingMods());
        return Component.translatable("message.renegades.class_unavailable", type.getDescription(), missing);
    }
}
