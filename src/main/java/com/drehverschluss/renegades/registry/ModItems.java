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

    private ModItems() {
    }
}