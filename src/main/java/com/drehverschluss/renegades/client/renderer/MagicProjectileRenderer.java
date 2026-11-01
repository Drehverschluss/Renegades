package com.drehverschluss.renegades.client.renderer;

import com.drehverschluss.renegades.compat.spellengine.SpellEngineCompat;
import com.drehverschluss.renegades.compat.spellengine.SpellEngineVisualsClient;
import com.drehverschluss.renegades.magic.entity.MagicProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * Spell projectiles: the built-in look is only the particle trail (spawned by the projectile itself), so nothing is
 * drawn here; with another mod's spell look (caster_visuals), that spell's projectile model is drawn.
 */
public class MagicProjectileRenderer extends EntityRenderer<MagicProjectile> {
    private final ItemRenderer itemRenderer;

    public MagicProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(MagicProjectile entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        // usesLookPart is only true with Spell Engine installed, so the Spell Engine class is never loaded without it.
        if (entity.usesLookPart(SpellEngineCompat.PROJECTILE_MODEL)) {
            SpellEngineVisualsClient.renderProjectile(entity, entity.lookSpell(), this.entityRenderDispatcher, this.itemRenderer, partialTick, poseStack,
                    buffers, light);
        }
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MagicProjectile entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
