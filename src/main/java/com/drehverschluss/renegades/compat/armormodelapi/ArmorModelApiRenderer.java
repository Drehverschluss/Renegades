package com.drehverschluss.renegades.compat.armormodelapi;

import com.drehverschluss.renegades.client.model.RenegadeBones;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.rpg_foundation.armor_api.client.ArmorOverrides;
import net.rpg_foundation.armor_api.client.ArmorRenderDispatcher;
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Renders Armor Model API (RPG Series) armor on renegades.
 * <p>
 * ArmorRenderDispatcher copies the part poses of the given HumanoidModel onto the armor's geo model and renders it in the
 * coordinate space of HumanoidArmorLayer. This class poses such a HumanoidModel from the animated armor* bones each frame
 * and calls the dispatcher once per slot. Only loaded when Armor Model API is installed.
 */
public final class ArmorModelApiRenderer {
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] POSE_BONES = {
            RenegadeBones.ARMOR_HEAD, RenegadeBones.ARMOR_BODY, RenegadeBones.ARMOR_RIGHT_ARM,
            RenegadeBones.ARMOR_LEFT_ARM, RenegadeBones.ARMOR_RIGHT_LEG, RenegadeBones.ARMOR_LEFT_LEG
    };
    // HumanoidModel space: x and y flipped compared to GeckoLib model space, origin 24 px (1.5 blocks) above the feet.
    private static final float HUMANOID_ORIGIN_Y = 1.5F;
    private static final Matrix4f FLIP_XY = new Matrix4f().scaling(-1.0F, -1.0F, 1.0F);

    private final HumanoidModel<LivingEntity> poseModel =
            new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));

    public boolean handles(ItemStack stack) {
        return !stack.isEmpty() && (ArmorRenderers.get(stack.getItem()) != null || ArmorOverrides.of(stack).takesOver());
    }

    /**
     * Makes GeckoLib record the model space matrices of the bones used for posing.
     */
    public void trackBones(BakedGeoModel model) {
        for (String boneName : POSE_BONES) {
            model.getBone(boneName).ifPresent(bone -> bone.setTrackingMatrices(true));
        }
    }

    /**
     * Must be called with the pose stack in GeckoLib model space, after all bones of the frame have been rendered.
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, RenegadeEntity entity, BakedGeoModel model, int packedLight) {
        boolean posed = false;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);

            if (!handles(stack)) {
                continue;
            }

            if (!posed) {
                applyPose(entity, model);
                posed = true;
            }

            poseStack.pushPose();
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0F, -HUMANOID_ORIGIN_Y, 0.0F);
            ArmorRenderDispatcher.render(poseStack, bufferSource, stack, entity, slot, packedLight, this.poseModel);
            poseStack.popPose();
        }
    }

    private void applyPose(RenegadeEntity entity, BakedGeoModel model) {
        // copyPropertiesTo also transfers these flags; EntityModel defaults to young = true (baby scaling).
        this.poseModel.young = entity.isBaby();
        this.poseModel.riding = entity.isPassenger();
        this.poseModel.crouching = entity.isCrouching();

        copyBonePose(model, RenegadeBones.ARMOR_HEAD, this.poseModel.head);
        this.poseModel.hat.copyFrom(this.poseModel.head);
        copyBonePose(model, RenegadeBones.ARMOR_BODY, this.poseModel.body);
        copyBonePose(model, RenegadeBones.ARMOR_RIGHT_ARM, this.poseModel.rightArm);
        copyBonePose(model, RenegadeBones.ARMOR_LEFT_ARM, this.poseModel.leftArm);
        copyBonePose(model, RenegadeBones.ARMOR_RIGHT_LEG, this.poseModel.rightLeg);
        copyBonePose(model, RenegadeBones.ARMOR_LEFT_LEG, this.poseModel.leftLeg);
    }

    /**
     * Converts the bone's model space matrix (origin at the bone pivot) into the position, ZYX rotation and scale of a
     * HumanoidModel part, so that ModelPart#translateAndRotate reproduces exactly the same transform.
     */
    private static void copyBonePose(BakedGeoModel model, String boneName, ModelPart part) {
        GeoBone bone = model.getBone(boneName).orElse(null);

        if (bone == null) {
            part.resetPose();
            return;
        }

        Matrix4f pose = new Matrix4f()
                .translation(0.0F, HUMANOID_ORIGIN_Y, 0.0F)
                .mul(FLIP_XY)
                .mul(bone.getModelSpaceMatrix())
                .mul(FLIP_XY);
        Vector3f translation = pose.getTranslation(new Vector3f());
        Vector3f scale = pose.getScale(new Vector3f());
        Vector3f rotation = pose.normalize3x3().getEulerAnglesZYX(new Vector3f());

        part.x = translation.x * 16.0F;
        part.y = translation.y * 16.0F;
        part.z = translation.z * 16.0F;
        part.xRot = rotation.x;
        part.yRot = rotation.y;
        part.zRot = rotation.z;
        part.xScale = scale.x;
        part.yScale = scale.y;
        part.zScale = scale.z;
    }
}