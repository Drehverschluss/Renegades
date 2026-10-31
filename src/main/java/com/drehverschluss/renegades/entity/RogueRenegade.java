package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.combat.brain.MobBrain;
import com.drehverschluss.renegades.combat.brain.RogueBrain;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.ai.RogueNavigation;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

public class RogueRenegade extends RenegadeEntity {
    private static final RawAnimation STANCE = RawAnimation.begin().thenLoop("animation.v2.trickster.stance");
    private static final RawAnimation ADVANCE = RawAnimation.begin().thenLoop("animation.v2.trickster.advance");
    private static final RawAnimation LATERAL_LEFT = RawAnimation.begin().thenLoop("animation.v2.trickster.lateral_left");
    private static final RawAnimation LATERAL_RIGHT = RawAnimation.begin().thenLoop("animation.v2.trickster.lateral_right");
    private static final RawAnimation BACKSTEP = RawAnimation.begin().thenLoop("animation.v2.trickster.backstep");

    public RogueRenegade(EntityType<? extends RogueRenegade> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 45.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 0.0D);
    }

    @Nullable
    @Override
    protected MobBrain createBrain() {
        return new RogueBrain(this);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new RogueNavigation(this);
    }

    /**
     * Dodge (ANARCHY's TricksterEntity#hurt): a heavy hit (more than 4 damage) from an attacker while fighting is
     * evaded completely when the dodge is off cooldown. Damage without an attacker (falling, fire) or that bypasses
     * invulnerability (/kill) is never dodged.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide()
                && RenegadesConfig.ENABLE_DODGE.get()
                && amount > RogueBrain.DODGE_DAMAGE_THRESHOLD
                && this.getTarget() != null
                && source.getEntity() != null
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && this.getCombatBrain() instanceof RogueBrain rogueBrain
                && rogueBrain.tryDodge()) {
            this.makeSound(SoundEvents.PILLAGER_AMBIENT);
            return false;
        }
        return super.hurt(source, amount);
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

        AnimationController<RogueRenegade> actions = new AnimationController<>(this, ACTION_CONTROLLER, 0, state -> PlayState.STOP);
        for (String animation : RogueBrain.ACTION_ANIMATIONS) {
            actions.triggerableAnim(animation, RawAnimation.begin().thenPlay(animation));
        }
        controllers.add(actions);
    }
}
