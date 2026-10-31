// Based on MercenaryBrain from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: shots use the held bow or crossbow like vanilla mobs and are only fired with line of sight, within range and
// with no ally in the line of fire; traps are an own entity with a cylindrical blast instead of the ANARCHY ability
// system and can be switched off in the config; firing positions avoid water, lava and other hazards; an emergency
// shove when the target gets too close; Quick Charge shortens the shot cooldowns; other numbers unchanged.
package com.drehverschluss.renegades.combat.brain;

import com.drehverschluss.renegades.combat.AttackMotion;
import com.drehverschluss.renegades.combat.CombatAction;
import com.drehverschluss.renegades.combat.GroundLocomotion;
import com.drehverschluss.renegades.combat.MovementProfile;
import com.drehverschluss.renegades.combat.SlashVisual;
import com.drehverschluss.renegades.combat.TrackingMode;
import com.drehverschluss.renegades.combat.geometry.AttackVolume;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.ArcherRenegade;
import com.drehverschluss.renegades.entity.ArcherTrap;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Archer combat: keeps 8-13 blocks from its target and shoots from a planted stance (aimed shot, or a four-arrow burst),
 * relocates to a new firing position with line of sight when the target gets too close, too far or out of sight,
 * snap-shoots and throws traps on the way, and punishes openings (the target stops attacking, lands, drinks or eats, is
 * low on health, or stands near a trap) with faster volleys.
 */
public class ArcherBrain extends MobBrain {
    private static final String FAMILY = "animation.v2.merc.";
    public static final String AIM_HEAVY = FAMILY + "aim_heavy";
    public static final String BURST = FAMILY + "burst";
    public static final String SNAP_SHOT = FAMILY + "snap_shot";
    public static final String TRAP_THROW = FAMILY + "trap_throw";
    public static final String PLANT = FAMILY + "plant";
    public static final String SHOVE = "animation.v2.jugg.shoulder_check";
    /**
     * All triggered animations of this brain; registered on the renegade's action controller on both sides.
     */
    public static final List<String> ACTION_ANIMATIONS = List.of(AIM_HEAVY, BURST, SNAP_SHOT, TRAP_THROW, PLANT, SHOVE);

    private static final float MELEE_THRESHOLD = 4.0F;
    private static final float ENGAGEMENT_THRESHOLD = 16.0F;
    private static final float FLEE_THRESHOLD = 20.0F;
    private static final float DAMAGE_EXCEEDED_THRESHOLD = 10.0F;
    private static final double OPTIMAL_NEAR = 8.0;
    private static final double OPTIMAL_FAR = 13.0;
    private static final double TOO_CLOSE = 7.0;
    private static final double TOO_FAR = 17.0;
    // Longest distance the archer shoots from.
    private static final double MAX_SHOT_RANGE = 24.0;
    private static final double SHOVE_RANGE = 2.2;
    private static final int PUNISH_BURST_DURATION = 40;
    private static final int REPOSITION_CD = 360;
    private static final int RELOCATE_TIMEOUT = 110;
    private static final double MIN_ANGLE_CHANGE = 45.0;
    private static final double TRAP_SCAN_RADIUS = 17.0;
    // Relocation paths stay at least this far from the target, circling it in steps of this angle.
    private static final double DETOUR_RADIUS = 7.0;
    private static final double DETOUR_STEP_DEGREES = 40.0;

    private static final String AIMED_SHOT_ID = "archer_aimed_shot";
    private static final String BURST_ID = "archer_burst";
    private static final String SNAP_SHOT_ID = "archer_snap_shot";
    private static final String TRAP_THROW_ID = "archer_trap_throw";
    private static final String SHOVE_ID = "archer_shove";
    private static final String PUNISH_COOLDOWN = "punish_burst";
    private static final String REPOSITION_COOLDOWN = "reposition";

    private final ArcherRenegade archer;
    private int nearbyTrapCount;
    private int ownTrapCount;
    private boolean wantsRepositionReview;
    @Nullable
    private Vec3 relocateGoal;
    private int relocateTicks;
    private boolean relocateRecomputed;
    private final CombatAction aimedShot;
    private final CombatAction burst;
    private final CombatAction snapShot;
    private final CombatAction trapThrow;
    private final CombatAction shove;

    public ArcherBrain(ArcherRenegade entity) {
        super(entity, Stance.PROBING, CombatState.PROBING);
        this.archer = entity;
        this.aimedShot = CombatAction.builder(AIMED_SHOT_ID)
                .animation(AIM_HEAVY)
                .timing(17, 3, 8)
                .motion(AttackMotion.PLANTED)
                .tracking(TrackingMode.REDUCED, TrackingMode.LOCKED)
                .cooldown(92)
                .onStart(ctx -> {
                    ctx.locomotion().arrest(MovementProfile.HARD_STOP);
                    this.archer.startAiming();
                })
                .onActiveStart(ctx -> this.shoot(ctx.liveTarget()))
                .onFinish(ctx -> this.archer.stopAiming())
                .build();
        this.burst = CombatAction.builder(BURST_ID)
                .animation(BURST)
                .timing(8, 24, 12)
                .motion(AttackMotion.PLANTED)
                .tracking(TrackingMode.REDUCED, TrackingMode.LOCKED)
                .cooldown(300)
                .onStart(ctx -> {
                    ctx.locomotion().arrest(MovementProfile.HARD_STOP);
                    this.archer.startAiming();
                })
                .onActiveTick(ctx -> {
                    if ((ctx.phaseTick() - 3) % 6 == 0 && ctx.phaseTick() <= 21) {
                        this.shoot(ctx.liveTarget());
                    }
                })
                .onFinish(ctx -> {
                    this.archer.stopAiming();
                    this.wantsRepositionReview = true;
                })
                .build();
        this.snapShot = CombatAction.builder(SNAP_SHOT_ID)
                .animation(SNAP_SHOT)
                .timing(4, 2, 4)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(140)
                .onStart(ctx -> this.archer.startAiming())
                .onActiveStart(ctx -> this.shoot(ctx.liveTarget()))
                .onFinish(ctx -> this.archer.stopAiming())
                .build();
        this.trapThrow = CombatAction.builder(TRAP_THROW_ID)
                .animation(TRAP_THROW)
                .timing(3, 3, 5)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.FULL, TrackingMode.FULL)
                .cooldown(320)
                .onActiveStart(ctx -> this.placePredictiveTraps())
                .build();
        // Emergency shove when the target is in its face: weak hit, strong knockback to win back distance.
        this.shove = CombatAction.builder(SHOVE_ID)
                .animation(SHOVE)
                .timing(6, 3, 8)
                .motion(AttackMotion.PRESSING)
                .advance(0.5)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(100)
                .volume(AttackVolume.cone(62.0F, 2.6))
                .weaponDamage()
                .knockback(1.15, 0.28)
                .slash(SlashVisual.blunt(ParticleTypes.POOF))
                .onActiveStart(ctx -> ctx.entity().playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9F, 1.0F))
                .build();
    }

    @Override
    protected AIStimulus createStimulus() {
        return new AIStimulus(this.entity, MELEE_THRESHOLD, ENGAGEMENT_THRESHOLD, FLEE_THRESHOLD, DAMAGE_EXCEEDED_THRESHOLD);
    }

    @Override
    protected List<Transition> stanceTransitions() {
        return List.of(
                new Transition(Stance.PROBING, s -> s.timeSincePlayerUsedDefensive < 100, Stance.OPPORTUNIST, 1),
                new Transition(Stance.OPPORTUNIST, s -> s.timeSincePlayerUsedDefensive > 200, Stance.PROBING, 1)
        );
    }

    @Override
    protected List<Transition> combatStateTransitions() {
        return List.of(
                new Transition(CombatState.PROBING, this::isPunishableWindow, CombatState.PUNISHING, 10),
                new Transition(CombatState.PUNISHING, s -> this.cooldowns.isReady(PUNISH_COOLDOWN), CombatState.PROBING, 10)
        );
    }

    @Override
    protected List<PhaseTransition> definePhaseTransitions() {
        return List.of();
    }

    /**
     * Shots have no height limit: a raised target (pillar, ledge) is shot at as long as it is in sight and range.
     */
    @Override
    protected CombatAction reachAction() {
        return this.aimedShot;
    }

    @Override
    protected void onCombatStateEnter(BrainState state) {
        if (state == CombatState.PUNISHING) {
            this.cooldowns.trigger(PUNISH_COOLDOWN, PUNISH_BURST_DURATION);
            this.wantsRepositionReview = true;
        }
    }

    @Override
    protected void executeCurrentCombatState() {
        LivingEntity target = this.entity.getTarget();
        if (target == null) {
            return;
        }
        this.countTraps();
        if (!this.actions.isBusy()) {
            this.archer.stopAiming();
        }
        if (this.actionOwnsMovement() || this.tryChain()) {
            return;
        }
        if (this.tryShove(target)) {
            return;
        }
        if (this.relocateGoal != null) {
            this.tickRelocating(target);
        } else if (this.currentCombatState == CombatState.PROBING) {
            this.tickProbing(target);
        } else {
            this.tickPunishing(target);
        }
    }

    private boolean tryShove(LivingEntity target) {
        if (!this.actions.isBusy() && this.stimulus.targetDistance <= SHOVE_RANGE && this.cooldowns.isReady(SHOVE_ID)
                && this.canStrike(target, this.shove)) {
            this.relocateGoal = null;
            return this.startAction(this.shove);
        }
        return false;
    }

    private void tickProbing(LivingEntity target) {
        this.holdBand(target, MovementProfile.DISCIPLINED);
        this.fireIfPlanted(target, false);
    }

    private void tickPunishing(LivingEntity target) {
        this.holdBand(target, MovementProfile.DISCIPLINED.urgent(1.15, 1.2, 1.1F));
        this.fireIfPlanted(target, true);
    }

    private void holdBand(LivingEntity target, MovementProfile profile) {
        this.locomotion.faceTarget(target, profile.turnDegrees());
        double distance = this.stimulus.targetDistance;
        boolean wantsNewPosition = distance < TOO_CLOSE || distance > TOO_FAR || !this.stimulus.lineOfSightToTarget || this.wantsRepositionReview;
        if (wantsNewPosition && this.cooldowns.isReady(REPOSITION_COOLDOWN)) {
            this.wantsRepositionReview = false;
            this.beginRelocation(target);
            return;
        }
        this.wantsRepositionReview = false;
        if (distance > OPTIMAL_FAR || !this.stimulus.lineOfSightToTarget) {
            this.locomotion.setFallbackGoal(target.position(), 1.2);
            this.locomotion.pressure(target, OPTIMAL_FAR, profile);
        } else if (distance < OPTIMAL_NEAR) {
            this.locomotion.retreatFrom(target, OPTIMAL_NEAR, profile);
        } else {
            this.locomotion.arrest(profile);
        }
    }

    private void fireIfPlanted(LivingEntity target, boolean punishing) {
        if (this.actions.isBusy() || this.locomotion.speed() > 0.04 || !this.canShoot(target)) {
            return;
        }
        double reload = this.archer.reloadFactor();
        if (this.cooldowns.isReady(BURST_ID) && (punishing || this.stimulus.targetDistance > OPTIMAL_NEAR)) {
            this.startAction(this.burst, (punishing ? 0.75 : 1.0) * reload);
        } else if (this.cooldowns.isReady(AIMED_SHOT_ID)) {
            this.startAction(this.aimedShot, (punishing ? 0.7 : 1.0) * reload);
        }
    }

    /**
     * A shot needs a bow or crossbow, line of sight, range, and nobody the archer must not hit in the line of fire; an
     * ally in the way makes the archer look for another position.
     */
    private boolean canShoot(LivingEntity target) {
        if (!this.archer.hasRangedWeapon() || !this.stimulus.lineOfSightToTarget || this.stimulus.targetDistance > MAX_SHOT_RANGE) {
            return false;
        }
        if (this.archer.isLineOfFireBlocked(target)) {
            this.wantsRepositionReview = true;
            return false;
        }
        return true;
    }

    private void shoot(@Nullable LivingEntity target) {
        if (target != null && target.isAlive() && this.entity.hasLineOfSight(target) && !this.archer.isLineOfFireBlocked(target)) {
            this.archer.shootAt(target);
        }
    }

    private void beginRelocation(LivingEntity target) {
        Vec3 destination = this.chooseFiringPosition(target);
        if (destination == null) {
            this.cooldowns.trigger(REPOSITION_COOLDOWN, 40);
            return;
        }
        this.cooldowns.trigger(REPOSITION_COOLDOWN, this.entity.scaledCooldown(REPOSITION_CD, 0.4, 1));
        this.relocateGoal = destination;
        this.relocateTicks = 0;
        this.relocateRecomputed = false;
        this.actions.cancel();
        this.archer.stopAiming();
    }

    private void tickRelocating(LivingEntity target) {
        MovementProfile profile = MovementProfile.TACTICAL_SPRINT;
        this.relocateTicks++;
        Vec3 goal = this.relocateGoal;
        double remaining = GroundLocomotion.horizontal(goal.subtract(this.entity.position())).length();
        if (remaining < 0.9) {
            this.arriveAtPosition(target);
            return;
        }
        if (this.relocateTicks > RELOCATE_TIMEOUT) {
            if (!this.relocateRecomputed) {
                this.relocateRecomputed = true;
                this.relocateTicks = 0;
                Vec3 retry = this.chooseFiringPosition(target);
                if (retry != null) {
                    this.relocateGoal = retry;
                    return;
                }
            }
            this.arriveAtPosition(target);
            return;
        }

        this.locomotion.facePosition(goal.x, goal.z, profile.turnDegrees());
        this.locomotion.setFallbackGoal(goal, 1.5);
        this.locomotion.seekPosition(this.relocationWaypoint(target, goal), profile);
        if (!this.actions.isBusy()) {
            if (RenegadesConfig.ENABLE_TRAPS.get() && this.cooldowns.isReady(TRAP_THROW_ID) && this.entity.hasLineOfSight(target)) {
                this.startAction(this.trapThrow);
            } else if (this.cooldowns.isReady(SNAP_SHOT_ID) && this.relocateTicks > 12 && this.canShoot(target)) {
                this.startAction(this.snapShot, this.archer.reloadFactor());
            }
        }
    }

    /**
     * The firing position often lies on another side of the target. Instead of running straight past it (into melee
     * range), the archer then circles around it at a safe radius until the straight way to the goal is clear.
     */
    private Vec3 relocationWaypoint(LivingEntity target, Vec3 goal) {
        Vec3 position = this.entity.position();
        Vec3 centre = target.position();
        Vec3 toGoal = GroundLocomotion.horizontal(goal.subtract(position));
        double length = toGoal.length();
        if (length < 1.0E-4) {
            return goal;
        }
        Vec3 direction = toGoal.scale(1.0 / length);
        Vec3 toCentre = GroundLocomotion.horizontal(centre.subtract(position));
        double along = Mth.clamp(toCentre.dot(direction), 0.0, length);
        double passDistance = toCentre.subtract(direction.scale(along)).length();
        // Only a path that actually passes the target closer than both the detour radius and the current distance
        // needs a detour (moving straight outwards never does).
        if (passDistance >= Math.min(DETOUR_RADIUS, toCentre.length() - 0.5)) {
            return goal;
        }

        Vec3 fromCentre = GroundLocomotion.horizontal(position.subtract(centre));
        Vec3 goalFromCentre = GroundLocomotion.horizontal(goal.subtract(centre));
        double currentAngle = Math.atan2(fromCentre.z, fromCentre.x);
        double goalAngle = Math.atan2(goalFromCentre.z, goalFromCentre.x);
        double turn = Mth.wrapDegrees(Math.toDegrees(goalAngle - currentAngle));
        double step = Math.toRadians(Math.copySign(Math.min(Math.abs(turn), DETOUR_STEP_DEGREES), turn));
        double radius = Math.max(DETOUR_RADIUS, fromCentre.length());
        double angle = currentAngle + step;
        return new Vec3(centre.x + Math.cos(angle) * radius, position.y, centre.z + Math.sin(angle) * radius);
    }

    private void arriveAtPosition(LivingEntity target) {
        this.relocateGoal = null;
        this.relocateTicks = 0;
        this.actions.cancel();
        this.locomotion.cancelCommitted();
        this.locomotion.arrest(MovementProfile.HARD_STOP);
        this.entity.getNavigation().stop();
        this.locomotion.faceTarget(target, 40.0F);
        this.entity.playActionAnimation(RenegadeEntity.ACTION_CONTROLLER, PLANT);
        this.cooldowns.clear(AIMED_SHOT_ID);
    }

    /**
     * Picks a standable spot 8-13 blocks from the target, at least 45 degrees around it from the current position,
     * with line of sight to the target; higher spots and spots away from own traps score better.
     */
    @Nullable
    private Vec3 chooseFiringPosition(LivingEntity target) {
        Level level = this.entity.level();
        Vec3 current = GroundLocomotion.horizontal(this.entity.position().subtract(target.position()));
        double currentAngle = Math.toDegrees(Math.atan2(current.z, current.x));
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30 + this.entity.getRandom().nextInt(12));
            double radius = OPTIMAL_NEAR + this.entity.getRandom().nextDouble() * (OPTIMAL_FAR - OPTIMAL_NEAR);
            Vec3 candidate = this.groundAt(target.position().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius));
            if (candidate == null) {
                continue;
            }
            float angleChange = Math.abs(Mth.wrapDegrees((float) (Math.toDegrees(angle) - currentAngle)));
            if (angleChange < MIN_ANGLE_CHANGE || !this.hasLineOfSight(candidate, target)) {
                continue;
            }
            double score = angleChange * 0.05 + (candidate.y - target.getY()) * 0.4;
            int trapsNear = level.getEntitiesOfClass(ArcherTrap.class,
                    this.entity.getBoundingBox().move(candidate.subtract(this.entity.position())).inflate(3.5), ArcherTrap::isAlive).size();
            score -= trapsNear * 1.5;
            score += this.entity.getRandom().nextDouble() * 0.6;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    @Nullable
    private Vec3 groundAt(Vec3 candidate) {
        Level level = this.entity.level();
        BlockPos origin = BlockPos.containing(candidate);
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos pos = origin.above(dy);
            BlockPos below = pos.below();
            if (level.getBlockState(below).isRedstoneConductor(level, below)
                    && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                boolean safe = level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty()
                        && !level.getBlockState(below).is(Blocks.MAGMA_BLOCK);
                return safe ? new Vec3(candidate.x, pos.getY(), candidate.z) : null;
            }
        }
        return null;
    }

    private boolean hasLineOfSight(Vec3 from, LivingEntity target) {
        Vec3 eye = from.add(0.0, this.entity.getEyeHeight(), 0.0);
        return this.entity.level().clip(new ClipContext(eye, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.entity))
                .getType() != HitResult.Type.BLOCK;
    }

    /**
     * Throws three traps in a fan towards where the target will be in about a second.
     */
    private void placePredictiveTraps() {
        LivingEntity target = this.entity.getTarget();
        if (target == null || this.entity.level().isClientSide() || !RenegadesConfig.ENABLE_TRAPS.get()) {
            return;
        }
        Vec3 velocity = GroundLocomotion.horizontal(this.stimulus.targetVelocity);
        Vec3 predicted = target.position().add(velocity.scale(20.0));
        Vec3 toPredicted = GroundLocomotion.horizontal(predicted.subtract(this.entity.position()));
        if (toPredicted.lengthSqr() < 1.0E-4) {
            toPredicted = this.locomotion.facing();
        }
        Vec3 centre = toPredicted.normalize();
        double spread = Math.toRadians(24.0 + Math.min(46.0, velocity.length() * 190.0));
        double throwSpeed = Mth.clamp(toPredicted.length() * 0.022, 0.16, 0.42);
        for (int i = -1; i <= 1; i++) {
            Vec3 direction = centre.yRot((float) (i * spread));
            ArcherTrap trap = new ArcherTrap(this.entity, this.entity.getEyePosition(), direction.scale(throwSpeed).add(0.0, 0.12, 0.0));
            this.entity.level().addFreshEntity(trap);
            this.entity.level().playSound(null, trap.getX(), trap.getY(), trap.getZ(), SoundEvents.ARMOR_STAND_PLACE, SoundSource.HOSTILE, 0.75F, 0.8F);
        }
    }

    private void countTraps() {
        List<ArcherTrap> traps = this.entity.level().getEntitiesOfClass(ArcherTrap.class, this.entity.getBoundingBox().inflate(TRAP_SCAN_RADIUS), ArcherTrap::isAlive);
        this.nearbyTrapCount = traps.size();
        this.ownTrapCount = (int) traps.stream().filter(trap -> trap.getOwner() == this.entity).count();
    }

    private boolean isPunishableWindow(AIStimulus s) {
        return this.relocateGoal == null
                && (s.timeSinceLastPlayerAttack > 15 || s.playerJustLanded || s.playerIsCasting || s.playerHealthPct < 0.3F
                || this.nearbyTrapCount > 0 && s.targetDistance < 5.0F);
    }

    public boolean isRelocating() {
        return this.relocateGoal != null;
    }

    @Override
    public void onTargetLost() {
        this.relocateGoal = null;
        this.archer.stopAiming();
        super.onTargetLost();
    }

    @Override
    public String describe() {
        return super.describe() + String.format(Locale.ROOT, ", target %.1f blocks", this.stimulus.targetDistance)
                + (this.relocateGoal != null ? ", relocating" : "") + ", traps " + this.ownTrapCount;
    }

    public enum CombatState implements BrainState {
        PROBING,
        PUNISHING;

        @Override
        public String id() {
            return this.name();
        }
    }

    public enum Stance implements BrainState {
        PROBING,
        OPPORTUNIST;

        @Override
        public String id() {
            return this.name();
        }
    }
}
