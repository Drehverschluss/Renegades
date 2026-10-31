package com.drehverschluss.renegades.client.renderer;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.entity.ArcherTrap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders the archer's trap with the trap model of ANARCHY - RPG Minibosses (CC0-1.0), at half size like there.
 */
public class ArcherTrapRenderer extends GeoEntityRenderer<ArcherTrap> {
    public ArcherTrapRenderer(EntityRendererProvider.Context context) {
        super(context, new TrapModel());
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(ArcherTrap trap, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // ANARCHY skips the first ticks so the trap does not flash at the archer's eyes.
        if (trap.tickCount < 2) {
            return;
        }
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-trap.getYRot() - 90.0F));
        super.render(trap, 0.0F, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    protected void applyRotations(ArcherTrap trap, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
    }

    private static class TrapModel extends GeoModel<ArcherTrap> {
        private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Renegades.MODID, "geo/entity/trap.geo.json");
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Renegades.MODID, "textures/entity/trap.png");
        private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(Renegades.MODID, "animations/entity/trap.animation.json");

        @Override
        public ResourceLocation getModelResource(ArcherTrap trap) {
            return MODEL;
        }

        @Override
        public ResourceLocation getTextureResource(ArcherTrap trap) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(ArcherTrap trap) {
            return ANIMATIONS;
        }
    }
}
