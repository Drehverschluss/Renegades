// Based on RogueBrain from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: knife throw spawns the ThrowingKnife projectile instead of the ANARCHY ability system; no slash ribbons;
// stealth can be switched off in the config; holds and throws knives while the target is out of melee reach; knife damage
// factor 0.6 instead of 1.2; other numbers unchanged.
package com.drehverschluss.renegades.combat.brain;

import com.drehverschluss.renegades.combat.AttackMotion;
import com.drehverschluss.renegades.combat.CombatAction;
import com.drehverschluss.renegades.combat.GroundLocomotion;
import com.drehverschluss.renegades.combat.MovementProfile;
import com.drehverschluss.renegades.combat.SlashVisual;
import com.drehverschluss.renegades.combat.TrackingMode;
import com.drehverschluss.renegades.combat.geometry.AttackVolume;
import com.drehverschluss.renegades.combat.geometry.SwingDynamics;
import com.drehverschluss.renegades.combat.geometry.SwingPath;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.drehverschluss.renegades.entity.ThrowingKnife;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Rogue combat: stalks the target in a wide orbit (invisible for a while) and throws knives, then ambushes it from
 * the side with crossing slashes once the target lowers its guard, rolls in and out, dodges heavy hits and withdraws
 * when exposed for too long or badly hurt.
 * <p>
 * Stances: SHADOW (waiting), ASSAULT (approach window open), RECOIL (low health). Combat states: STALKING, AMBUSHING,
 * WITHDRAWING.
 */
public class RogueBrain extends MobBrain {
    private static final String FAMILY = "animation.v2.trickster.";
    public static final String CROSS_SLASH_L = FAMILY + "cross_slash_l";
    public static final String CROSS_SLASH_R = FAMILY + "cross_slash_r";
    public static final String THROW = FAMILY + "throw_mobile";
    public static final String ROLL = FAMILY + "roll";
    public static final String DODGE_LEFT = FAMILY + "dodge_left";
    public static final String DODGE_RIGHT = FAMILY + "dodge_right";
    /**
     * All triggered animations of this brain; registered on the renegade's action controller on both sides.
     */
    public static final List<String> ACTION_ANIMATIONS = List.of(CROSS_SLASH_L, CROSS_SLASH_R, THROW, ROLL, DODGE_LEFT, DODGE_RIGHT);

    private static final float MELEE_THRESHOLD = 3.0F;
    private static final float ENGAGEMENT_THRESHOLD = 16.0F;
    private static final float FLEE_THRESHOLD = 20.0F;
    private static final float DAMAGE_EXCEEDED_THRESHOLD = 4.0F;
    private static final float RECOIL_HEALTH_PCT = 0.35F;
    private static final int AMBUSH_CD = 400;
    private static final int AMBUSH_RESET_BUFFER = 40;
    private static final int STEALTH_CD = 480;
    private static final int STEALTH_DURATION = 320;
    private static final double ORBIT_RADIUS = 8.0;
    private static final int MAX_EXPOSURE_TICKS = 100;
    private static final double LANE_RADIUS = 3.4;
    private static final float THROW_MIN_RANGE = 4.0F;
    private static final float THROW_MAX_RANGE = 20.0F;
    // A heavy hit above this damage can be dodged (TricksterEntity#hurt).
    public static final float DODGE_DAMAGE_THRESHOLD = 4.0F;

    private static final String ROLL_ID = "rogue_roll";
    private static final String THROW_ID = "rogue_throw";
    private static final String AMBUSH_COOLDOWN = "ambush";
    private static final String AMBUSH_RESET_COOLDOWN = "ambush_reset";
    private static final String STEALTH_COOLDOWN = "stealth";
    private static final String DODGE_COOLDOWN = "dodge";

    private int exposureTicks = 0;
    private int orbitDirection = 1;
    private int orbitFlipTimer = 0;
    @Nullable
    private Vec3 ambushLane;
    private int ambushSide = 1;
    private final CombatAction crossSlashLeft;
    private final CombatAction crossSlashRight;
    private final CombatAction knifeThrow;
    private final CombatAction roll;

    public RogueBrain(RenegadeEntity entity) {
        super(entity, Stance.SHADOW, CombatState.STALKING);
        this.roll = CombatAction.builder(ROLL_ID)
                .animation(ROLL)
                .timing(3, 13, 3)
                .motion(AttackMotion.COMMITTED_VECTOR)
                .committed(0.44, 13, 0.0, 0.0)
                .tracking(TrackingMode.REDUCED, TrackingMode.LOCKED)
                .cooldown(160)
                .onStart(ctx -> ctx.entity().playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 1.4F))
                .build();
        this.crossSlashLeft = this.crossSlash("rogue_cross_slash_l", CROSS_SLASH_L, -1);
        this.crossSlashRight = this.crossSlash("rogue_cross_slash_r", CROSS_SLASH_R, 1);
        this.knifeThrow = CombatAction.builder(THROW_ID)
                .animation(THROW)
                .timing(5, 9, 5)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(160)
                .onActiveStart(ctx -> this.throwKnife())
                .onActiveTick(ctx -> {
                    // Second knife of the same throw, with the throwing animation replayed.
                    if (ctx.phaseTick() == 5) {
                        this.entity.playActionAnimation(RenegadeEntity.ACTION_CONTROLLER, THROW);
                        this.throwKnife();
                    }
                })
                .build();
    }

    private CombatAction crossSlash(String id, String animation, int side) {
        return CombatAction.builder(id)
                .animation(animation)
                .timing(5, 8, 7)
                .motion(AttackMotion.CROSS_THROUGH)
                .committed(0.4, 8, 0.0, 0.0)
                .tracking(TrackingMode.FULL, TrackingMode.LOCKED)
                .cooldown(52)
                .volume(AttackVolume.sweep((side > 0 ? SwingPath.rightToLeft(110.0F) : SwingPath.leftToRight(110.0F)).withDynamics(SwingDynamics.FORCEFUL), 2.8F, 2.2)
                        .withInnerRadius(0.4))
                .weaponDamage()
                .knockback(0.3, 0.08)
                .slash(SlashVisual.light(ParticleTypes.CRIT))
                .onStart(ctx -> ctx.setScratch(side))
                .onFinish(ctx -> {
                    if (this.exposureTicks > 60 && this.cooldowns.isReady(ROLL_ID)) {
                        this.chain(this.roll);
                    }
                })
                .build();
    }

    @Override
    protected AIStimulus createStimulus() {
        return new AIStimulus(this.entity, MELEE_THRESHOLD, ENGAGEMENT_THRESHOLD, FLEE_THRESHOLD, DAMAGE_EXCEEDED_THRESHOLD);
    }

    @Override
    protected List<Transition> stanceTransitions() {
        return List.of(
                new Transition(Stance.SHADOW, this::isApproachWindowOpen, Stance.ASSAULT, 5),
                new Transition(Stance.ASSAULT, s -> s.ownHealthPct < RECOIL_HEALTH_PCT, Stance.RECOIL, 10),
                new Transition(Stance.RECOIL, s -> s.timeSinceLastHit > 100, Stance.SHADOW, 5)
        );
    }

    @Override
    protected List<Transition> combatStateTransitions() {
        return List.of(
                new Transition(CombatState.STALKING, s -> this.isApproachWindowOpen(s) && this.cooldowns.isReady(AMBUSH_COOLDOWN), CombatState.AMBUSHING, 10),
                new Transition(CombatState.AMBUSHING, s -> this.exposureTicks > MAX_EXPOSURE_TICKS || s.ownHealthPct < RECOIL_HEALTH_PCT, CombatState.WITHDRAWING, 10),
                new Transition(CombatState.AMBUSHING, s -> !this.cooldowns.isReady(AMBUSH_COOLDOWN) && this.exposureTicks > 40, CombatState.STALKING, 5),
                new Transition(CombatState.WITHDRAWING,
                        s -> (!s.lineOfSightToTarget || s.timeSinceLastHit > 160) && this.cooldowns.isReady(AMBUSH_RESET_COOLDOWN),
                        CombatState.STALKING, 10)
        );
    }

    @Override
    protected List<PhaseTransition> definePhaseTransitions() {
        return List.of();
    }

    @Override
    protected void onCombatStateEnter(BrainState state) {
        if (state == CombatState.STALKING) {
            this.exposureTicks = 0;
            this.ambushLane = null;
            if (RenegadesConfig.ENABLE_STEALTH.get() && this.cooldowns.isReady(STEALTH_COOLDOWN)) {
                this.cooldowns.trigger(STEALTH_COOLDOWN, STEALTH_CD);
                this.entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, STEALTH_DURATION, 0));
            }
        } else if (state == CombatState.AMBUSHING) {
            this.entity.removeEffect(MobEffects.INVISIBILITY);
            this.cooldowns.trigger(AMBUSH_COOLDOWN, AMBUSH_CD);
            this.exposureTicks = 0;
            this.ambushLane = this.chooseAmbushLane();
        } else if (state == CombatState.WITHDRAWING) {
            this.entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1));
            this.cooldowns.trigger(AMBUSH_RESET_COOLDOWN, AMBUSH_RESET_BUFFER);
            this.ambushLane = null;
            if (this.cooldowns.isReady(ROLL_ID) && this.entity.getTarget() != null && !this.actions.isBusy()) {
                this.startRollAway();
            }
        }
    }

    @Override
    protected void executeCurrentCombatState() {
        LivingEntity target = this.entity.getTarget();
        if (target == null) {
            return;
        }

        if (--this.orbitFlipTimer <= 0) {
            this.orbitFlipTimer = 60 + this.entity.getRandom().nextInt(60);
            if (this.entity.getRandom().nextFloat() < 0.5F) {
                this.orbitDirection = -this.orbitDirection;
            }
        }

        if (this.actionOwnsMovement()) {
            return;
        }
        if (this.handleUnreachableTarget(target)) {
            // Out of melee reach (pillar, ledge): knives still fly.
            this.tryThrow(target, THROW_MIN_RANGE);
            return;
        }
        if (this.tryChain()) {
            return;
        }

        if (this.currentCombatState == CombatState.STALKING) {
            this.tickStalking(target);
        } else if (this.currentCombatState == CombatState.AMBUSHING) {
            this.tickAmbushing(target);
        } else {
            this.tickWithdrawing(target);
        }
    }

    /**
     * The cross slash decides whether the target can be fought in melee (both slashes share the same height).
     */
    @Override
    protected CombatAction reachAction() {
        return this.crossSlashRight;
    }

    private void tickStalking(LivingEntity target) {
        MovementProfile profile = MovementProfile.AGILE_ORBIT;
        this.locomotion.faceTarget(target, profile.turnDegrees());
        float dist = this.stimulus.targetDistance;
        if (dist <= 14.0F && this.stimulus.lineOfSightToTarget) {
            this.locomotion.setFallbackGoal(target.position(), 1.0);
            this.locomotion.orbit(target, ORBIT_RADIUS, this.orbitDirection, profile);
        } else {
            this.locomotion.navigateTo(target.getX(), target.getY(), target.getZ(), 1.0);
        }
        this.tryThrow(target, THROW_MIN_RANGE);
    }

    private void tickAmbushing(LivingEntity target) {
        this.exposureTicks++;
        MovementProfile profile = MovementProfile.AGILE_BURST;
        float dist = this.stimulus.targetDistance;
        Vec3 lane = this.ambushLane;
        if (lane != null && dist > MELEE_THRESHOLD) {
            Vec3 toLane = GroundLocomotion.horizontal(lane.subtract(this.entity.position()));
            if (toLane.length() >= 1.0) {
                this.locomotion.faceTarget(target, profile.turnDegrees());
                this.locomotion.setFallbackGoal(lane, 1.6);
                this.locomotion.seekPosition(lane, profile);
                this.tryLaneAttack(target, dist);
                return;
            }
            this.ambushLane = null;
        }

        this.locomotion.faceTarget(target, profile.turnDegrees());
        if (dist > MELEE_THRESHOLD) {
            this.locomotion.setFallbackGoal(target.position(), 1.6);
            this.locomotion.pressure(target, 2.0, profile);
        } else {
            this.locomotion.crossPast(target, this.ambushSide, MovementProfile.AGILE_ORBIT);
        }
        this.tryLaneAttack(target, dist);
    }

    private void tryLaneAttack(LivingEntity target, float distance) {
        if (this.actions.isBusy()) {
            return;
        }
        if (distance <= LANE_RADIUS) {
            CombatAction slash = this.ambushSide >= 0 ? this.crossSlashRight : this.crossSlashLeft;
            if (this.canStrike(target, slash)) {
                this.startAction(slash);
            }
        } else if (distance > 5.0F && this.cooldowns.isReady(ROLL_ID) && this.exposureTicks > 20 && this.stimulus.lineOfSightToTarget) {
            this.startRollToward(target);
        }
    }

    private void tickWithdrawing(LivingEntity target) {
        MovementProfile profile = MovementProfile.AGILE_ORBIT;
        this.locomotion.faceTarget(target, profile.turnDegrees());
        Vec3 exit = this.entity.position().add(this.locomotion.retreatVector(target).scale(10.0));
        this.locomotion.setFallbackGoal(exit, 1.3);
        this.locomotion.retreatFrom(target, 14.0, profile.withMaxSpeed(profile.maxSpeed() * 1.25));
        this.tryThrow(target, 5.0F);
    }

    private void tryThrow(LivingEntity target, float minDistance) {
        float dist = this.stimulus.targetDistance;
        if (!this.actions.isBusy() && this.cooldowns.isReady(THROW_ID) && this.canStrike(target, this.knifeThrow)
                && dist > minDistance && dist < THROW_MAX_RANGE) {
            this.startAction(this.knifeThrow);
        }
    }

    /**
     * Each knife needs free line of sight at the moment it leaves the hand, so no knife is thrown into a wall.
     */
    private void throwKnife() {
        LivingEntity target = this.entity.getTarget();
        if (target != null && target.isAlive() && this.entity.hasLineOfSight(target)) {
            this.entity.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            ThrowingKnife.throwAt(this.entity, target);
        }
    }

    private void startRollToward(LivingEntity target) {
        if (this.startAction(this.roll)) {
            this.locomotion.snapFacing(GroundLocomotion.horizontal(target.position().subtract(this.entity.position())));
        }
    }

    private void startRollAway() {
        LivingEntity target = this.entity.getTarget();
        if (target != null && this.startAction(this.roll)) {
            this.locomotion.snapFacing(this.locomotion.retreatVector(target));
        }
    }

    /**
     * The target lowers its guard: it has not attacked for a moment, just landed nearby, or is badly hurt.
     */
    private boolean isApproachWindowOpen(AIStimulus s) {
        return s.timeSinceLastPlayerAttack > 16 || s.playerJustLanded && s.targetDistance < 16.0F || s.playerHealthPct < 0.4F;
    }

    /**
     * Picks a standable point next to the target, preferring its back flanks (+-132 degrees from where it looks) over
     * the front flanks (+-62 degrees), and nearby points over far ones.
     */
    @Nullable
    private Vec3 chooseAmbushLane() {
        LivingEntity target = this.entity.getTarget();
        if (target == null) {
            return null;
        }

        Vec3 look = GroundLocomotion.horizontal(target.getLookAngle());
        if (look.lengthSqr() < 1.0E-4) {
            look = new Vec3(0.0, 0.0, 1.0);
        }
        look = look.normalize();

        float[] offsets = {62.0F, -62.0F, 132.0F, -132.0F};
        int[] weights = {2, 2, 3, 3};
        Vec3 best = null;
        int bestScore = -1;
        for (int i = 0; i < offsets.length; i++) {
            Vec3 candidate = target.position().add(look.yRot((float) Math.toRadians(offsets[i])).scale(LANE_RADIUS));
            if (this.isStandable(candidate)) {
                int proximity = (int) Math.max(0.0, 12.0 - this.entity.position().distanceTo(candidate));
                int score = weights[i] * 3 + proximity + this.entity.getRandom().nextInt(4);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                    this.ambushSide = offsets[i] > 0.0F ? 1 : -1;
                }
            }
        }
        return best;
    }

    private boolean isStandable(Vec3 position) {
        BlockPos pos = BlockPos.containing(position);
        Level level = this.entity.level();
        return level.getBlockState(pos.below()).isRedstoneConductor(level, pos.below())
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    /**
     * Dodges sideways when off cooldown (the caller cancels the damage): cancels the running action, plays the dodge
     * animation and moves 9 ticks away from the hit, to the side with free ground or straight back.
     */
    public boolean tryDodge() {
        LivingEntity target = this.entity.getTarget();
        if (!this.cooldowns.isReady(DODGE_COOLDOWN) || target == null) {
            return false;
        }

        this.cooldowns.trigger(DODGE_COOLDOWN, this.entity.scaledCooldown(160, 1.0, 80));
        Vec3 toTarget = GroundLocomotion.horizontal(target.position().subtract(this.entity.position()));
        if (toTarget.lengthSqr() < 1.0E-4) {
            toTarget = this.locomotion.facing();
        }

        Vec3 forward = toTarget.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        int side = this.locomotion.slipDirection(target);
        Vec3 escape = right.scale(side);
        if (!this.isStandable(this.entity.position().add(escape.scale(2.5)))) {
            escape = right.scale(-side);
            side = -side;
            if (!this.isStandable(this.entity.position().add(escape.scale(2.5)))) {
                escape = this.locomotion.retreatVector(target);
            }
        }

        this.actions.cancel();
        this.chain(null);
        this.entity.setPerforming(false);
        this.entity.playActionAnimation(RenegadeEntity.ACTION_CONTROLLER, side >= 0 ? DODGE_RIGHT : DODGE_LEFT);
        this.locomotion.beginCommittedMotion(escape, 0.46, 9, 0.0, 0.0);
        this.entity.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.5F);
        return true;
    }

    @Override
    public void onTargetLost() {
        this.entity.removeEffect(MobEffects.INVISIBILITY);
        this.ambushLane = null;
        super.onTargetLost();
    }

    @Override
    public String describe() {
        return super.describe() + ", exposure " + this.exposureTicks;
    }

    public enum CombatState implements BrainState {
        STALKING,
        AMBUSHING,
        WITHDRAWING;

        @Override
        public String id() {
            return this.name();
        }
    }

    public enum Stance implements BrainState {
        SHADOW,
        ASSAULT,
        RECOIL;

        @Override
        public String id() {
            return this.name();
        }
    }
}
