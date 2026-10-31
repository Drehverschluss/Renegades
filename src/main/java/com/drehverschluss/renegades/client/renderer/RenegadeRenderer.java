package com.drehverschluss.renegades.client.renderer;

import com.drehverschluss.renegades.client.model.RenegadeBones;
import com.drehverschluss.renegades.client.model.RenegadeModel;
import com.drehverschluss.renegades.client.renderer.layer.RenegadeArmorLayer;
import com.drehverschluss.renegades.client.renderer.layer.RenegadeHeldItemLayer;
import com.drehverschluss.renegades.compat.armormodelapi.ArmorModelApiCompat;
import com.drehverschluss.renegades.compat.armormodelapi.ArmorModelApiRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class RenegadeRenderer extends GeoEntityRenderer<RenegadeEntity> {
    // Only created when Armor Model API is installed, so its classes are never loaded otherwise.
    @Nullable
    private final ArmorModelApiRenderer armorModelApiRenderer;
    @Nullable
    private BakedGeoModel currentModel;
    // True while the bones of a fully invisible renegade are walked only for its armor and held items.
    private boolean hideBody;

    public RenegadeRenderer(EntityRendererProvider.Context context) {
        super(context, new RenegadeModel());
        this.shadowRadius = 0.5F;
        this.armorModelApiRenderer = ArmorModelApiCompat.isLoaded() ? new ArmorModelApiRenderer() : null;

        addRenderLayer(new RenegadeArmorLayer(this, this.armorModelApiRenderer == null
                ? stack -> false
                : this.armorModelApiRenderer::handles));
        addRenderLayer(new RenegadeHeldItemLayer(this));
    }

    @Override
    public void preRender(PoseStack poseStack, RenegadeEntity animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                          @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);

        this.currentModel = model;

        if (this.armorModelApiRenderer != null && !isReRender) {
            this.armorModelApiRenderer.trackBones(model);
        }
    }

    /**
     * GeckoLib skips the whole bone pass for an invisible entity (null render type), which would also hide armor and
     * held items. Like vanilla mobs, a renegade under Invisibility keeps showing its armor and items: the bones are
     * still walked with a buffer that discards the body's vertices.
     */
    @Override
    public void actuallyRender(PoseStack poseStack, RenegadeEntity animatable, BakedGeoModel model, @Nullable RenderType renderType,
                               MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick,
                               int packedLight, int packedOverlay, int colour) {
        this.hideBody = !isReRender && buffer == null && renderType == null && animatable.isInvisible();
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, this.hideBody ? DiscardingVertexConsumer.INSTANCE : buffer,
                isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, RenegadeEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);

        // Once the last root bone is done, every bone matrix of this frame is known and the pose stack is back in model space.
        if (this.armorModelApiRenderer != null && !isReRender && this.currentModel != null
                && bone == this.currentModel.topLevelBones().getLast()) {
            this.armorModelApiRenderer.render(poseStack, bufferSource, animatable, this.currentModel, packedLight);
        }
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        // Reference bones stay fully processed (transforms, matrix tracking, render layers); only their cubes are skipped.
        // This does not rely on the "neverRender" flag in the geo file, which Blockbench may drop on re-export.
        if (this.hideBody || RenegadeBones.REFERENCE_ONLY.contains(bone.getName())) {
            return;
        }

        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    @Override
    public void doPostRenderCleanup() {
        super.doPostRenderCleanup();
        this.currentModel = null;
        this.hideBody = false;
    }

    private enum DiscardingVertexConsumer implements VertexConsumer {
        INSTANCE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}