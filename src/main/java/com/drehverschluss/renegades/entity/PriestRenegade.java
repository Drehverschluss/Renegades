package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.combat.brain.MobBrain;
import com.drehverschluss.renegades.combat.brain.PriestBrain;
import com.drehverschluss.renegades.magic.HealingCaster;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
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
 * Healer that keeps its group alive: heals and shields wounded allies, attacks with holy bolts when nobody needs it.
 */
public class PriestRenegade extends RenegadeEntity implements HealingCaster {
    private static final RawAnimation STANCE = RawAnimation.begin().thenLoop("animation.v2.mage.stance");
    private static final RawAnimation ADVANCE = RawAnimation.begin().thenLoop("animation.v2.mage.advance");
    private static final RawAnimation LATERAL_LEFT = RawAnimation.begin().thenLoop("animation.v2.mage.lateral_left");
    private static final RawAnimation LATERAL_RIGHT = RawAnimation.begin().thenLoop("animation.v2.mage.lateral_right");
    private static final RawAnimation BACKSTEP = RawAnimation.begin().thenLoop("animation.v2.mage.backstep");
    // Leather robe: white body, golden hood and shoes.
    private static final int ROBE_WHITE = 0xF4F1E8;
    private static final int ROBE_GOLD = 0xE0B23A;

    public PriestRenegade(EntityType<? extends PriestRenegade> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 0.0D);
    }

    @Nullable
    @Override
    protected MobBrain createBrain() {
        return new PriestBrain(this);
    }

    /**
     * Dyes leather armor white and gold.
     */
    @Override
    public void applyPoolVariant(@Nullable String variant) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = this.getItemBySlot(slot);
            if (stack.is(ItemTags.DYEABLE)) {
                int color = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.FEET ? ROBE_GOLD : ROBE_WHITE;
                stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, true));
            }
        }
    }

    @Override
    public void onHealed(LivingEntity target, float amount) {
        if (this.getCombatBrain() instanceof PriestBrain brain) {
            brain.recordHeal(amount);
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

        AnimationController<PriestRenegade> actions = new AnimationController<>(this, ACTION_CONTROLLER, 0, state -> PlayState.STOP);
        for (String animation : PriestBrain.ACTION_ANIMATIONS) {
            actions.triggerableAnim(animation, animation.equals(PriestBrain.HEAL)
                    ? RawAnimation.begin().thenLoop(animation)
                    : RawAnimation.begin().thenPlay(animation));
        }
        controllers.add(actions);
    }
}
