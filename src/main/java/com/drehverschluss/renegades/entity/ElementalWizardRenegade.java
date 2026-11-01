package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.combat.brain.ElementalWizardBrain;
import com.drehverschluss.renegades.combat.brain.MobBrain;
import com.drehverschluss.renegades.magic.HealingCaster;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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
 * Control caster of the Elemental Wizards RPG class with an aqua, terra or wind element (from its equipment pool entry,
 * otherwise random). Only available with that mod (see RenegadeClasses).
 */
public class ElementalWizardRenegade extends RenegadeEntity implements HealingCaster {
    private static final RawAnimation STANCE = RawAnimation.begin().thenLoop("animation.v2.mage.stance");
    private static final RawAnimation ADVANCE = RawAnimation.begin().thenLoop("animation.v2.mage.advance");
    private static final RawAnimation LATERAL_LEFT = RawAnimation.begin().thenLoop("animation.v2.mage.lateral_left");
    private static final RawAnimation LATERAL_RIGHT = RawAnimation.begin().thenLoop("animation.v2.mage.lateral_right");
    private static final RawAnimation BACKSTEP = RawAnimation.begin().thenLoop("animation.v2.mage.backstep");
    private static final String TAG_VARIANT = "ElementalVariant";
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @Nullable
    private ElementalVariant variant;

    public ElementalWizardRenegade(EntityType<? extends ElementalWizardRenegade> entityType, Level level) {
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
        return new ElementalWizardBrain(this);
    }

    public ElementalVariant getVariant() {
        if (this.variant == null) {
            this.variant = ElementalVariant.random(this.getRandom());
        }
        return this.variant;
    }

    @Override
    public String visualVariant() {
        return this.getVariant().id();
    }

    /**
     * Takes the pool entry's element (random if none or unknown) and dyes leather armor in the element's colour.
     */
    @Override
    public void applyPoolVariant(@Nullable String id) {
        ElementalVariant chosen = ElementalVariant.byId(id);
        if (id != null && chosen == null) {
            Renegades.LOGGER.warn("Unknown elemental wizard variant '{}' in an equipment pool, expected aqua, terra or wind; picking one at random", id);
        }
        this.variant = chosen != null ? chosen : ElementalVariant.random(this.getRandom());
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = this.getItemBySlot(slot);
            if (stack.is(ItemTags.DYEABLE)) {
                stack.set(DataComponents.DYED_COLOR, new DyedItemColor(this.variant.robeColor(), true));
            }
        }
    }

    @Override
    public void onHealed(LivingEntity target, float amount) {
        if (this.getCombatBrain() instanceof ElementalWizardBrain brain) {
            brain.recordHeal(amount);
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
        ElementalVariant saved = ElementalVariant.byId(tag.getString(TAG_VARIANT));
        if (saved != null) {
            this.variant = saved;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new MovementAnimationController<>(this, "movement", 5, state -> {
            if (this.isAggressive()) {
                return state.setAndContinue(switch (this.getCombatMoveState()) {
                    case STANCE -> STANCE;
                    case ADVANCE -> ADVANCE;
                    case LATERAL_LEFT -> LATERAL_LEFT;
                    case LATERAL_RIGHT -> LATERAL_RIGHT;
                    case BACKSTEP -> BACKSTEP;
                });
            }
            return state.setAndContinue(this.isWalking() ? WALK : IDLE);
        }).setAnimationSpeedHandler(RenegadeEntity::movementAnimationSpeed));

        AnimationController<ElementalWizardRenegade> actions = new AnimationController<>(this, ACTION_CONTROLLER, 0, state -> PlayState.STOP);
        for (String animation : ElementalWizardBrain.ACTION_ANIMATIONS) {
            RawAnimation raw = ElementalWizardBrain.LOOPED_ANIMATIONS.contains(animation)
                    ? RawAnimation.begin().thenLoop(animation) : RawAnimation.begin().thenPlay(animation);
            actions.triggerableAnim(animation, raw);
        }
        controllers.add(actions);
    }
}
