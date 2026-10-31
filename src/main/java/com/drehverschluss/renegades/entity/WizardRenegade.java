package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.combat.brain.MobBrain;
import com.drehverschluss.renegades.combat.brain.WizardBrain;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Spell caster with a fire, frost or arcane variant (from its equipment pool entry, otherwise random).
 */
public class WizardRenegade extends RenegadeEntity {
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.mob.walk_templar");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mob.idle");
    private static final RawAnimation STANCE = RawAnimation.begin().thenLoop("animation.v2.mage.stance");
    private static final RawAnimation ADVANCE = RawAnimation.begin().thenLoop("animation.v2.mage.advance");
    private static final RawAnimation LATERAL_LEFT = RawAnimation.begin().thenLoop("animation.v2.mage.lateral_left");
    private static final RawAnimation LATERAL_RIGHT = RawAnimation.begin().thenLoop("animation.v2.mage.lateral_right");
    private static final RawAnimation BACKSTEP = RawAnimation.begin().thenLoop("animation.v2.mage.backstep");
    private static final String TAG_VARIANT = "WizardVariant";
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @Nullable
    private WizardVariant variant;

    public WizardRenegade(EntityType<? extends WizardRenegade> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 35.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 0.0D);
    }

    @Nullable
    @Override
    protected MobBrain createBrain() {
        return new WizardBrain(this);
    }

    public WizardVariant getVariant() {
        if (this.variant == null) {
            this.variant = WizardVariant.random(this.getRandom());
        }
        return this.variant;
    }

    /**
     * Takes the pool entry's variant (random if none or unknown) and dyes leather armor in the school's colour.
     */
    @Override
    public void applyPoolVariant(@Nullable String id) {
        WizardVariant chosen = WizardVariant.byId(id);
        if (id != null && chosen == null) {
            Renegades.LOGGER.warn("Unknown wizard variant '{}' in an equipment pool, expected fire, frost or arcane; picking one at random", id);
        }
        this.variant = chosen != null ? chosen : WizardVariant.random(this.getRandom());
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = this.getItemBySlot(slot);
            if (stack.is(ItemTags.DYEABLE)) {
                stack.set(DataComponents.DYED_COLOR, new DyedItemColor(this.variant.robeColor(), true));
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_VARIANT, this.getVariant().id());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        WizardVariant saved = WizardVariant.byId(tag.getString(TAG_VARIANT));
        if (saved != null) {
            this.variant = saved;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (this.isAggressive()) {
                return state.setAndContinue(switch (this.getCombatMoveState()) {
                    case STANCE -> STANCE;
                    case ADVANCE -> ADVANCE;
                    case LATERAL_LEFT -> LATERAL_LEFT;
                    case LATERAL_RIGHT -> LATERAL_RIGHT;
                    case BACKSTEP -> BACKSTEP;
                });
            }
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }).setAnimationSpeedHandler(wizard -> wizard.isAggressive() && wizard.getCombatMoveState() != CombatMoveState.STANCE
                ? (double) wizard.getCombatMoveSpeed() : 1.0D));

        AnimationController<WizardRenegade> actions = new AnimationController<>(this, ACTION_CONTROLLER, 0, state -> PlayState.STOP);
        for (String animation : WizardBrain.ACTION_ANIMATIONS) {
            actions.triggerableAnim(animation, RawAnimation.begin().thenPlay(animation));
        }
        controllers.add(actions);
    }
}
