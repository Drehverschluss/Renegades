package com.drehverschluss.renegades;

import com.drehverschluss.renegades.entity.WarriorRenegade;
import com.drehverschluss.renegades.registry.ModEntityTypes;
import com.drehverschluss.renegades.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

@Mod(Renegades.MODID)
public class Renegades {
    public static final String MODID = "renegades";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Renegades(IEventBus modEventBus, ModContainer modContainer) {
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);

        modEventBus.addListener(Renegades::registerAttributes);
        modEventBus.addListener(Renegades::addCreativeTabContents);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.WARRIOR.get(), WarriorRenegade.createAttributes().build());
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.WARRIOR_SPAWN_EGG);
        }
    }
}