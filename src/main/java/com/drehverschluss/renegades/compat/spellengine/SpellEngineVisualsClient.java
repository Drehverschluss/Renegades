package com.drehverschluss.renegades.compat.spellengine;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.render.SpellProjectileRenderer;

/**
 * Client side of the Spell Engine look: draws a spell's projectile model for one of our projectiles with Spell Engine's
 * own renderer code. Only loaded when Spell Engine is installed.
 */
public final class SpellEngineVisualsClient {
    private SpellEngineVisualsClient() {
    }

    /**
     * Returns false if the spell (or its projectile model) is missing, so the caller can fall back.
     */
    public static boolean renderProjectile(Entity projectile, ResourceLocation id, EntityRenderDispatcher dispatcher, ItemRenderer itemRenderer,
                                           float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Spell.ProjectileData data = SpellEngineVisuals.projectileData(SpellEngineVisuals.spell(projectile.level(), id));
        if (data == null || data.client_data == null || data.client_data.composite_model == null) {
            return false;
        }
        // Same call as SpellProjectileRenderer#render with its default scale; no previous velocity is kept.
        SpellProjectileRenderer.renderComposite(1.0F, dispatcher, itemRenderer, data.client_data.composite_model, null, null, projectile,
                partialTick, true, poseStack, buffers, light);
        return true;
    }
}
