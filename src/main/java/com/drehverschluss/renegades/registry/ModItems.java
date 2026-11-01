package com.drehverschluss.renegades.registry;

import com.drehverschluss.renegades.Renegades;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Renegades.MODID);

    public static final DeferredItem<RenegadeSpawnEggItem> WARRIOR_SPAWN_EGG = ITEMS.register("warrior_spawn_egg",
            () -> new RenegadeSpawnEggItem(ModEntityTypes.WARRIOR, 0x9A9A9A, 0x5C3A21, new Item.Properties()));

    public static final DeferredItem<RenegadeSpawnEggItem> ROGUE_SPAWN_EGG = ITEMS.register("rogue_spawn_egg",
            () -> new RenegadeSpawnEggItem(ModEntityTypes.ROGUE, 0x2E2A33, 0x7A1E1E, new Item.Properties()));

    public static final DeferredItem<RenegadeSpawnEggItem> PALADIN_SPAWN_EGG = ITEMS.register("paladin_spawn_egg",
            () -> new RenegadeSpawnEggItem(ModEntityTypes.PALADIN, 0xD8D2C0, 0xC9A23A, new Item.Properties()));

    public static final DeferredItem<RenegadeSpawnEggItem> ARCHER_SPAWN_EGG = ITEMS.register("archer_spawn_egg",
            () -> new RenegadeSpawnEggItem(ModEntityTypes.ARCHER, 0x3F5A2A, 0x8B6A3E, new Item.Properties()));

    public static final DeferredItem<RenegadeSpawnEggItem> WIZARD_SPAWN_EGG = ITEMS.register("wizard_spawn_egg",
            () -> new RenegadeSpawnEggItem(ModEntityTypes.WIZARD, 0x2B2F6B, 0xD9A441, new Item.Properties()));
    public static final DeferredItem<RenegadeSpawnEggItem> PRIEST_SPAWN_EGG = ITEMS.register("priest_spawn_egg",
            () -> new RenegadeSpawnEggItem(ModEntityTypes.PRIEST, 0xF4F1E8, 0xE0B23A, new Item.Properties()));
    public static final DeferredItem<RenegadeSpawnEggItem> ELEMENTAL_WIZARD_SPAWN_EGG = ITEMS.register("elemental_wizard_spawn_egg",
            () -> new RenegadeSpawnEggItem(ModEntityTypes.ELEMENTAL_WIZARD, 0x2E6E8E, 0xC9A64B, new Item.Properties()));

    private ModItems() {
    }
}