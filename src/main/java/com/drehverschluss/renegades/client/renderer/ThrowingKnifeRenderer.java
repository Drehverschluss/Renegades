package com.drehverschluss.renegades.client.renderer;

import com.drehverschluss.renegades.entity.ThrowingKnife;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Renders the thrown knife's item along its flight direction, tumbling end over end.
 */
public class ThrowingKnifeRenderer extends EntityRenderer<ThrowingKnife> {
    private static final float SPIN_DEGREES_PER_TICK = 40.0F;
    private final ItemRenderer itemRenderer;

    public ThrowingKnifeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ThrowingKnife knife, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        // Same orientation as vanilla arrows: local +X points along the flight direction.
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, knife.yRotO, knife.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, knife.xRotO, knife.getXRot())));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-(knife.tickCount + partialTick) * SPIN_DEGREES_PER_TICK));
        // Item sprites point diagonally up and right; turn the tip onto the flight axis.
        poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0F));
        poseStack.scale(0.8F, 0.8F, 0.8F);
        this.itemRenderer.renderStatic(knife.getItem(), ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY, poseStack, bufferSource,
                knife.level(), knife.getId());
        poseStack.popPose();
        super.render(knife, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(ThrowingKnife knife) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
