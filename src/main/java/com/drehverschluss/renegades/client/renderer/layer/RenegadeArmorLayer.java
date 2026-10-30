package com.drehverschluss.renegades.client.renderer.layer;

import com.drehverschluss.renegades.client.model.RenegadeBones;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

/**
 * Renders worn armor on the armor* bones. Stacks matched by {@code renderedElsewhere} are skipped (Armor Model API).
 */
public class RenegadeArmorLayer extends ItemArmorGeoLayer<RenegadeEntity> {
    private final Predicate<ItemStack> renderedElsewhere;

    public RenegadeArmorLayer(GeoRenderer<RenegadeEntity> renderer, Predicate<ItemStack> renderedElsewhere) {
        super(renderer);
        this.renderedElsewhere = renderedElsewhere;
    }

    @Nullable
    @Override
    protected ItemStack getArmorItemForBone(GeoBone bone, RenegadeEntity animatable) {
        ItemStack stack = switch (bone.getName()) {
            case RenegadeBones.ARMOR_HEAD -> this.helmetStack;
            case RenegadeBones.ARMOR_BODY, RenegadeBones.ARMOR_RIGHT_ARM, RenegadeBones.ARMOR_LEFT_ARM -> this.chestplateStack;
            case RenegadeBones.ARMOR_BELT, RenegadeBones.ARMOR_RIGHT_LEG, RenegadeBones.ARMOR_LEFT_LEG -> this.leggingsStack;
            case RenegadeBones.ARMOR_RIGHT_BOOT, RenegadeBones.ARMOR_LEFT_BOOT -> this.bootsStack;
            default -> null;
        };

        if (stack == null || stack.isEmpty() || this.renderedElsewhere.test(stack)) {
            return null;
        }

        return stack;
    }

    @Override
    protected EquipmentSlot getEquipmentSlotForBone(GeoBone bone, ItemStack stack, RenegadeEntity animatable) {
        return switch (bone.getName()) {
            case RenegadeBones.ARMOR_HEAD -> EquipmentSlot.HEAD;
            case RenegadeBones.ARMOR_BELT, RenegadeBones.ARMOR_RIGHT_LEG, RenegadeBones.ARMOR_LEFT_LEG -> EquipmentSlot.LEGS;
            case RenegadeBones.ARMOR_RIGHT_BOOT, RenegadeBones.ARMOR_LEFT_BOOT -> EquipmentSlot.FEET;
            default -> EquipmentSlot.CHEST;
        };
    }

    @Override
    protected ModelPart getModelPartForBone(GeoBone bone, EquipmentSlot slot, ItemStack stack, RenegadeEntity animatable, HumanoidModel<?> baseModel) {
        return switch (bone.getName()) {
            case RenegadeBones.ARMOR_HEAD -> baseModel.head;
            case RenegadeBones.ARMOR_RIGHT_ARM -> baseModel.rightArm;
            case RenegadeBones.ARMOR_LEFT_ARM -> baseModel.leftArm;
            case RenegadeBones.ARMOR_RIGHT_LEG, RenegadeBones.ARMOR_RIGHT_BOOT -> baseModel.rightLeg;
            case RenegadeBones.ARMOR_LEFT_LEG, RenegadeBones.ARMOR_LEFT_BOOT -> baseModel.leftLeg;
            default -> baseModel.body;
        };
    }

    @Override
    protected void prepModelPartForRender(PoseStack poseStack, GeoBone bone, ModelPart sourcePart) {
        super.prepModelPartForRender(poseStack, bone, sourcePart);

        // GeoEntityRenderer has already applied the bone's own rotation to the pose stack; applying it again
        // through the model part would double it whenever an animation rotates an armor bone directly.
        sourcePart.xRot = 0;
        sourcePart.yRot = 0;
        sourcePart.zRot = 0;
    }
}