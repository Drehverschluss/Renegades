package com.drehverschluss.renegades;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Renegades.MODID)
public class Renegades {
    public static final String MODID = "renegades";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Renegades(IEventBus modEventBus, ModContainer modContainer) {
    }
}