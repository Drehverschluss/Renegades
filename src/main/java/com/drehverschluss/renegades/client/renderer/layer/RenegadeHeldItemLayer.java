package com.drehverschluss.renegades.client.renderer.layer;

import com.drehverschluss.renegades.client.model.RenegadeBones;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Renders the main hand item on itemBone.
 */
public class RenegadeHeldItemLayer extends BlockAndItemGeoLayer<RenegadeEntity> {
    public RenegadeHeldItemLayer(GeoRenderer<RenegadeEntity> renderer) {
        super(renderer);
    }

    @Nullable
    @Override
    protected ItemStack getStackForBone(GeoBone bone, RenegadeEntity animatable) {
        if (!RenegadeBones.ITEM.equals(bone.getName())) {
            return null;
        }

        ItemStack stack = animatable.getMainHandItem();
        return stack.isEmpty() ? null : stack;
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, RenegadeEntity animatable) {
        return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, RenegadeEntity animatable, MultiBufferSource bufferSource,
                                      float partialTick, int packedLight, int packedOverlay) {
        // BlockAndItemGeoLayer applies itemBone's rotation a second time, so its 45 degree rest pose ends up as the
        // -90 degree hand rotation of vanilla's ItemInHandLayer. This offset moves the origin to vanilla's hand point.
        poseStack.translate(0.0F, 2.0F / 16.0F, -2.0F / 16.0F);
        super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
    }
}