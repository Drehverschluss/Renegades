package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.combat.brain.MobBrain;
import com.drehverschluss.renegades.combat.brain.PaladinBrain;
import javax.annotation.Nullable;
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

public class PaladinRenegade extends RenegadeEntity {
    private static final RawAnimation STANCE = RawAnimation.begin().thenLoop("animation.v2.jugg.stance");
    private static final RawAnimation ADVANCE = RawAnimation.begin().thenLoop("animation.v2.jugg.advance");
    private static final RawAnimation LATERAL_LEFT = RawAnimation.begin().thenLoop("animation.v2.jugg.lateral_left");
    private static final RawAnimation LATERAL_RIGHT = RawAnimation.begin().thenLoop("animation.v2.jugg.lateral_right");
    private static final RawAnimation BACKSTEP = RawAnimation.begin().thenLoop("animation.v2.jugg.backstep");
    private static final float BRACE_DAMAGE_FACTOR = 0.5F;
    private static final double BRACE_KNOCKBACK_FACTOR = 0.1D;
    private static final byte SHIELD_BREAK_EVENT = 30;

    public PaladinRenegade(EntityType<? extends PaladinRenegade> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 75.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 0.0D);
    }

    @Nullable
    @Override
    protected MobBrain createBrain() {
        return new PaladinBrain(this);
    }

    @Nullable
    private PaladinBrain paladinBrain() {
        return this.getCombatBrain() instanceof PaladinBrain brain ? brain : null;
    }

    /**
     * Bracing without a shield halves all damage (ANARCHY's JuggernautEntity#hurt). With a shield, the raised shield
     * blocks hits from the front by the vanilla rules instead; hits from behind go through.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        PaladinBrain brain = this.paladinBrain();
        if (!this.level().isClientSide() && brain != null && brain.isBracing() && !brain.isShieldBrace()) {
            amount *= BRACE_DAMAGE_FACTOR;
        }
        return super.hurt(source, amount);
    }

    /**
     * Barely moved by knockback while bracing (ANARCHY's JuggernautEntity#knockback).
     */
    @Override
    public void knockback(double strength, double x, double z) {
        PaladinBrain brain = this.paladinBrain();
        super.knockback(brain != null && brain.isBracing() ? strength * BRACE_KNOCKBACK_FACTOR : strength, x, z);
    }

    /**
     * Vanilla only lets axes disable the shields of players; the paladin's shield follows the same rule.
     */
    @Override
    protected void blockUsingShield(LivingEntity attacker) {
        super.blockUsingShield(attacker);
        PaladinBrain brain = this.paladinBrain();
        if (brain != null && attacker.canDisableShield()) {
            brain.onShieldDisabled();
            this.level().broadcastEntityEvent(this, SHIELD_BREAK_EVENT);
        }
    }

    /**
     * Landing from its own leap does not hurt (the landing spot is checked to be at most 1.5 blocks lower).
     */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        PaladinBrain brain = this.paladinBrain();
        if (brain != null && brain.isLeaping()) {
            return false;
        }
        return super.causeFallDamage(fallDistance, multiplier, source);
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
            return state.setAndContinue(this.isWalking() ? WALK : IDLE);
        }).setAnimationSpeedHandler(RenegadeEntity::movementAnimationSpeed));

        AnimationController<PaladinRenegade> actions = new AnimationController<>(this, ACTION_CONTROLLER, 0, state -> PlayState.STOP);
        for (String animation : PaladinBrain.ACTION_ANIMATIONS) {
            actions.triggerableAnim(animation, RawAnimation.begin().thenPlay(animation));
        }
        controllers.add(actions);
    }
}
