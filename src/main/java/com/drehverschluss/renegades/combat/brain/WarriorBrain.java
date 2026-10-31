// Based on TemplarBrain from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: no Divine Fall (spell); parry on RenegadeEntity's action controller; numbers unchanged.
package com.drehverschluss.renegades.combat.brain;

import com.drehverschluss.renegades.combat.AttackMotion;
import com.drehverschluss.renegades.combat.CombatAction;
import com.drehverschluss.renegades.combat.MovementProfile;
import com.drehverschluss.renegades.combat.SlashVisual;
import com.drehverschluss.renegades.combat.TrackingMode;
import com.drehverschluss.renegades.combat.geometry.AttackPlane;
import com.drehverschluss.renegades.combat.geometry.AttackShape;
import com.drehverschluss.renegades.combat.geometry.AttackVolume;
import com.drehverschluss.renegades.combat.geometry.SwingDynamics;
import com.drehverschluss.renegades.combat.geometry.SwingPath;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Warrior combat: alternating heavy sweeps (right-to-left / left-to-right), a dash that chains into a rising dash cut
 * to close gaps, and a parry with counter-strike. Two stances: CRUSHING (close range) and CONDEMNING (target far away).
 */
public class WarriorBrain extends MobBrain {
    private static final String FAMILY = "animation.v2.templar.";
    public static final String SWEEP_RL = FAMILY + "sweep_rl";
    public static final String SWEEP_LR = FAMILY + "sweep_lr";
    public static final String DASH = FAMILY + "dash";
    public static final String DASH_CUT = FAMILY + "dash_cut";
    public static final String PARRY = FAMILY + "parry";
    /**
     * All triggered animations of this brain; registered on the renegade's action controller on both sides.
     */
    public static final List<String> ACTION_ANIMATIONS = List.of(SWEEP_RL, SWEEP_LR, DASH, DASH_CUT, PARRY);

    private static final float MELEE_THRESHOLD = 4.0F;
    private static final float ENGAGEMENT_THRESHOLD = 10.0F;
    private static final float CONDEMN_THRESHOLD = 8.0F;
    private static final float FLEE_THRESHOLD = 12.0F;
    private static final float SWEEP_RANGE = 5.0F;
    private static final double PREFERRED_RANGE = 2.8;
    private static final String PARRY_COOLDOWN = "parry";
    public static final float PARRY_RANGE = 5.5F;
    private static final float DASH_CUT_RANGE = 5.5F;

    private int engagementDurationTicks = 0;
    private boolean sweepRightToLeft = true;
    private final CombatAction sweepRl;
    private final CombatAction sweepLr;
    private final CombatAction dash;
    private final CombatAction dashCut;

    public WarriorBrain(RenegadeEntity entity) {
        super(entity, Stance.CRUSHING, Stance.CRUSHING);
        this.sweepRl = this.sweep("warrior_sweep_rl", SWEEP_RL, true);
        this.sweepLr = this.sweep("warrior_sweep_lr", SWEEP_LR, false);
        this.dashCut = CombatAction.builder("warrior_dash_cut")
                .animation(DASH_CUT)
                .timing(6, 4, 8)
                .motion(AttackMotion.PRESSING)
                .advance(0.75)
                .tracking(TrackingMode.REDUCED, TrackingMode.LOCKED)
                .cooldown(40)
                .volume(new AttackVolume(
                        AttackShape.DIAGONAL_ARC,
                        SwingPath.rising(120.0F, AttackPlane.DIAGONAL_STEEP).withDynamics(SwingDynamics.FORCEFUL),
                        4.0, 0.7, 2.0, 2.2, 0.3, 0.35, 1.2))
                .weaponDamage()
                .knockback(0.5, 0.2)
                .slash(SlashVisual.light(ParticleTypes.ENCHANTED_HIT))
                .build();
        this.dash = CombatAction.builder("warrior_dash")
                .animation(DASH)
                .timing(8, 10, 5)
                .motion(AttackMotion.COMMITTED_VECTOR)
                .committed(0.62, 10, 0.0, 0.0)
                .tracking(TrackingMode.REDUCED, TrackingMode.LOCKED)
                .cooldown(160)
                .urgentRecoveryTrim(3)
                .volume(AttackVolume.path(4.0, 2.4, 2.6))
                .scaledDamage(0.4F)
                .knockback(0.7, 0.25)
                .multiHit(8)
                .slash(SlashVisual.light(ParticleTypes.ENCHANTED_HIT).withCount(10))
                .onStart(ctx -> ctx.entity().playSound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.9F, 0.8F))
                .onRecoveryStart(ctx -> {
                    LivingEntity target = ctx.liveTarget();
                    if (target != null && entity.distanceTo(target) <= DASH_CUT_RANGE
                            && entity.hasLineOfSight(target) && withinVerticalReach(entity, target, this.dashCut.volume())) {
                        this.chain(this.dashCut);
                    }
                })
                .build();
    }

    private CombatAction sweep(String id, String animation, boolean rightToLeft) {
        return CombatAction.builder(id)
                .animation(animation)
                .timing(10, 5, 7)
                .motion(AttackMotion.PRESSING)
                .windupDrift(0.25)
                .advance(1.25)
                .tracking(TrackingMode.REDUCED, TrackingMode.LOCKED)
                .cooldown(52)
                .urgentRecoveryTrim(3)
                .volume(AttackVolume.sweep((rightToLeft ? SwingPath.rightToLeft(180.0F) : SwingPath.leftToRight(180.0F)).withDynamics(SwingDynamics.FORCEFUL), 4.0, 3.0))
                .weaponDamage()
                .knockback(0.55, 0.12)
                .slash(SlashVisual.light(ParticleTypes.ENCHANTED_HIT).withCount(22))
                .build();
    }

    @Override
    protected AIStimulus createStimulus() {
        return new AIStimulus(this.entity, MELEE_THRESHOLD, ENGAGEMENT_THRESHOLD, FLEE_THRESHOLD, CONDEMN_THRESHOLD);
    }

    @Override
    protected List<Transition> stanceTransitions() {
        return List.of(
                new Transition(Stance.CRUSHING, s -> s.targetDistance > CONDEMN_THRESHOLD, Stance.CONDEMNING, 10),
                new Transition(Stance.CONDEMNING, s -> s.targetInMeleeRange, Stance.CRUSHING, 10)
        );
    }

    @Override
    protected List<Transition> combatStateTransitions() {
        return List.of();
    }

    @Override
    protected List<PhaseTransition> definePhaseTransitions() {
        return List.of();
    }

    @Override
    protected void onStanceEnter(BrainState stance) {
        this.currentCombatState = stance;
        if (stance == Stance.CRUSHING) {
            this.engagementDurationTicks = 0;
        }
    }

    @Override
    protected void executeCurrentCombatState() {
        LivingEntity target = this.entity.getTarget();
        if (target != null && !this.actionOwnsMovement() && !this.handleUnreachableTarget(target) && !this.tryChain()) {
            if (this.currentStance == Stance.CRUSHING) {
                this.tickCrushing(target);
            } else {
                this.tickCondemning(target);
            }
        }
    }

    /**
     * The sweep decides whether the target can be fought at all (both sweeps share the same height).
     */
    @Override
    protected CombatAction reachAction() {
        return this.sweepRl;
    }

    private void tickCrushing(LivingEntity target) {
        this.engagementDurationTicks++;
        MovementProfile profile = this.crushProfile();
        this.locomotion.faceTarget(target, profile.turnDegrees());
        float distance = this.stimulus.targetDistance;
        if (distance <= FLEE_THRESHOLD && this.stimulus.lineOfSightToTarget) {
            this.locomotion.setFallbackGoal(target.position(), 1.1);
            this.locomotion.pressure(target, PREFERRED_RANGE, profile);
        } else {
            this.locomotion.navigateTo(target.getX(), target.getY(), target.getZ(), 1.0);
        }

        if (!this.actions.isBusy() && this.stimulus.lineOfSightToTarget) {
            if (this.cooldowns.isReady("warrior_dash") && distance > SWEEP_RANGE && distance < FLEE_THRESHOLD && this.canStrike(target, this.dash)) {
                this.startAction(this.dash);
            } else if (distance <= SWEEP_RANGE && this.canStrike(target, this.sweepRl)) {
                this.startAction(this.nextSweep());
            }
        }
    }

    private MovementProfile crushProfile() {
        double scale = 1.0 + Math.min(0.28, this.engagementDurationTicks * 4.0E-4);
        return MovementProfile.RELENTLESS.withMaxSpeed(MovementProfile.RELENTLESS.maxSpeed() * scale);
    }

    private void tickCondemning(LivingEntity target) {
        this.engagementDurationTicks = 0;
        MovementProfile profile = this.condemnProfile();
        this.locomotion.faceTarget(target, profile.turnDegrees());
        float distance = this.stimulus.targetDistance;
        if (distance <= 14.0F && this.stimulus.lineOfSightToTarget) {
            this.locomotion.setFallbackGoal(target.position(), 1.4);
            this.locomotion.pressure(target, PREFERRED_RANGE, profile);
        } else {
            this.locomotion.navigateTo(target.getX(), target.getY(), target.getZ(), 1.4);
        }

        if (!this.actions.isBusy()) {
            if (this.cooldowns.isReady("warrior_dash") && distance > SWEEP_RANGE && distance < 14.0F && this.canStrike(target, this.dash)) {
                this.startAction(this.dash, 0.7);
            } else if (distance <= SWEEP_RANGE && this.canStrike(target, this.sweepRl)) {
                this.startAction(this.nextSweep());
            }
        }
    }

    private MovementProfile condemnProfile() {
        float dist = this.stimulus.targetDistance;
        float t = Mth.clamp((dist - CONDEMN_THRESHOLD) / 16.0F, 0.0F, 1.0F);
        return MovementProfile.CONDEMN.withMaxSpeed(MovementProfile.CONDEMN.maxSpeed() * (1.0 + t * 0.35)).urgent(1.0, 1.0 + t * 0.4, 1.0F);
    }

    private CombatAction nextSweep() {
        this.sweepRightToLeft = !this.sweepRightToLeft;
        return this.sweepRightToLeft ? this.sweepRl : this.sweepLr;
    }

    /**
     * Parry when off cooldown; plays the parry animation. The caller deals the counter-strike and halves the damage.
     */
    public boolean tryParry() {
        if (!this.cooldowns.isReady(PARRY_COOLDOWN)) {
            return false;
        }

        this.cooldowns.trigger(PARRY_COOLDOWN, Math.max(160, 320 - (int) (320.0 * this.entity.getCooldownCoeff())));
        this.entity.playActionAnimation(RenegadeEntity.ACTION_CONTROLLER, PARRY);
        return true;
    }

    public enum Stance implements BrainState {
        CRUSHING,
        CONDEMNING;

        @Override
        public String id() {
            return this.name();
        }
    }
}
