package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.combat.brain.MobBrain;
import com.drehverschluss.renegades.combat.brain.WarriorBrain;
import com.drehverschluss.renegades.config.RenegadesConfig;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

public class WarriorRenegade extends RenegadeEntity {
    private static final RawAnimation STANCE = RawAnimation.begin().thenLoop("animation.v2.templar.stance");
    private static final RawAnimation ADVANCE = RawAnimation.begin().thenLoop("animation.v2.templar.advance");
    private static final RawAnimation LATERAL_LEFT = RawAnimation.begin().thenLoop("animation.v2.templar.lateral_left");
    private static final RawAnimation LATERAL_RIGHT = RawAnimation.begin().thenLoop("animation.v2.templar.lateral_right");
    private static final RawAnimation BACKSTEP = RawAnimation.begin().thenLoop("animation.v2.templar.backstep");

    public WarriorRenegade(EntityType<? extends WarriorRenegade> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 0.0D);
    }

    @Nullable
    @Override
    protected MobBrain createBrain() {
        return new WarriorBrain(this);
    }

    /**
     * Parry (ANARCHY's TemplarEntity#hurt): a direct melee hit from close range while no action is running is parried
     * when the parry is off cooldown: parry animation, counter-strike on the attacker and half damage.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide()
                && RenegadesConfig.ENABLE_PARRY.get()
                && !this.isPerforming()
                && this.invulnerableTime <= 10
                && source.isDirect()
                && source.getEntity() instanceof LivingEntity attacker
                && attacker.distanceTo(this) < WarriorBrain.PARRY_RANGE
                && this.getCombatBrain() instanceof WarriorBrain warriorBrain
                && warriorBrain.tryParry()) {
            this.doHurtTarget(attacker);
            amount *= 0.5F;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_PLACE, this.getSoundSource(), 1.0F, 1.0F);
        }
        return super.hurt(source, amount);
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

        AnimationController<WarriorRenegade> actions = new AnimationController<>(this, ACTION_CONTROLLER, 0, state -> PlayState.STOP);
        for (String animation : WarriorBrain.ACTION_ANIMATIONS) {
            actions.triggerableAnim(animation, RawAnimation.begin().thenPlay(animation));
        }
        controllers.add(actions);
    }
}
