package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.combat.brain.ForcemasterBrain;
import com.drehverschluss.renegades.combat.brain.MobBrain;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Forcemaster of the Forcemaster RPG class: a combo fighter with knuckles in both hands that charges up with every
 * technique and unloads in a huge announced blow. Only available with that mod (see RenegadeClasses).
 */
public class ForcemasterRenegade extends RenegadeEntity {
    private static final RawAnimation STANCE = RawAnimation.begin().thenLoop("animation.v2.trickster.stance");
    private static final RawAnimation ADVANCE = RawAnimation.begin().thenLoop("animation.v2.trickster.advance");
    private static final RawAnimation LATERAL_LEFT = RawAnimation.begin().thenLoop("animation.v2.trickster.lateral_left");
    private static final RawAnimation LATERAL_RIGHT = RawAnimation.begin().thenLoop("animation.v2.trickster.lateral_right");
    private static final RawAnimation BACKSTEP = RawAnimation.begin().thenLoop("animation.v2.trickster.backstep");

    public ForcemasterRenegade(EntityType<? extends ForcemasterRenegade> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 0.0D);
    }

    @Nullable
    @Override
    protected MobBrain createBrain() {
        return new ForcemasterBrain(this);
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

        AnimationController<ForcemasterRenegade> actions = new AnimationController<>(this, ACTION_CONTROLLER, 0, state -> PlayState.STOP);
        for (String animation : ForcemasterBrain.ACTION_ANIMATIONS) {
            actions.triggerableAnim(animation, RawAnimation.begin().thenPlay(animation));
        }
        controllers.add(actions);
    }
}
