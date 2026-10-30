package com.drehverschluss.renegades;

import com.drehverschluss.renegades.client.renderer.RenegadeRenderer;
import com.drehverschluss.renegades.registry.ModEntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = Renegades.MODID, dist = Dist.CLIENT)
public class RenegadesClient {
    public RenegadesClient(IEventBus modEventBus) {
        modEventBus.addListener(RenegadesClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.WARRIOR.get(), RenegadeRenderer::new);
    }
}