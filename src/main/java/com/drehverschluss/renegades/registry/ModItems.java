package com.drehverschluss.renegades.registry;

import com.drehverschluss.renegades.Renegades;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Renegades.MODID);

    public static final DeferredItem<DeferredSpawnEggItem> WARRIOR_SPAWN_EGG = ITEMS.register("warrior_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntityTypes.WARRIOR, 0x9A9A9A, 0x5C3A21, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> ROGUE_SPAWN_EGG = ITEMS.register("rogue_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntityTypes.ROGUE, 0x2E2A33, 0x7A1E1E, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> PALADIN_SPAWN_EGG = ITEMS.register("paladin_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntityTypes.PALADIN, 0xD8D2C0, 0xC9A23A, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> ARCHER_SPAWN_EGG = ITEMS.register("archer_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntityTypes.ARCHER, 0x3F5A2A, 0x8B6A3E, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> WIZARD_SPAWN_EGG = ITEMS.register("wizard_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntityTypes.WIZARD, 0x2B2F6B, 0xD9A441, new Item.Properties()));

    private ModItems() {
    }
}