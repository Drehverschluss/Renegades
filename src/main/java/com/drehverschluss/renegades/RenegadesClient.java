package com.drehverschluss.renegades;

import com.drehverschluss.renegades.client.renderer.ArcherTrapRenderer;
import com.drehverschluss.renegades.client.renderer.MagicProjectileRenderer;
import com.drehverschluss.renegades.client.renderer.RenegadeRenderer;
import com.drehverschluss.renegades.client.renderer.ThrowingKnifeRenderer;
import com.drehverschluss.renegades.registry.ModEntityTypes;
import net.minecraft.client.renderer.entity.NoopRenderer;
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
        event.registerEntityRenderer(ModEntityTypes.ROGUE.get(), RenegadeRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.PALADIN.get(), RenegadeRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.ARCHER.get(), RenegadeRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.ARCHER_TRAP.get(), ArcherTrapRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.WIZARD.get(), RenegadeRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.PRIEST.get(), RenegadeRenderer::new);
        // Same RenegadeRenderer and with it the one shared RenegadeModel (needed for Iris shadow passes, see RenegadeRenderer).
        event.registerEntityRenderer(ModEntityTypes.ELEMENTAL_WIZARD.get(), RenegadeRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.BERSERKER.get(), RenegadeRenderer::new);
        // Spell projectiles and clouds are drawn with particles (spawned client-side in their tick); projectiles with
        // another mod's spell look also draw that spell's model.
        event.registerEntityRenderer(ModEntityTypes.MAGIC_PROJECTILE.get(), MagicProjectileRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.MAGIC_CLOUD.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.THROWING_KNIFE.get(), ThrowingKnifeRenderer::new);
    }
}