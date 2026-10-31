// Based on JuggernautBrain from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: the ground impact ("juggernaut/pound") is a cylindrical area strike of our combat system instead of the ANARCHY
// ability system, with damage lowered to attack damage x 1.0; bracing raises a real shield when the paladin carries one;
// leaps only to safe, reachable landing spots; bracing and leaping can be switched off in the config; no slash ribbons
// or ANARCHY sounds/effects; other numbers unchanged.
package com.drehverschluss.renegades.combat.brain;

import com.drehverschluss.renegades.combat.ActionContext;
import com.drehverschluss.renegades.combat.AreaStrike;
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
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * Paladin combat: a slow, heavy advance with a wide overhead swing, a shoulder check up close, a ground slam, a leap
 * that lands with a ground impact, and a spinning whirlwind. After a heavy hit (or once at low health) it braces:
 * stands still behind its shield (or, without one, takes half damage). Below 20 % health it enters its last stand:
 * no more bracing, faster movement and all big attacks ready again.
 */
public class PaladinBrain extends MobBrain {
    private static final String FAMILY = "animation.v2.jugg.";
    public static final String HEAVY_SWING = FAMILY + "heavy_swing";
    public static final String SHOULDER_CHECK = FAMILY + "shoulder_check";
    public static final String SLAM = FAMILY + "slam";
    public static final String LEAP = FAMILY + "leap";
    public static final String SPIN = FAMILY + "spin";
    public static final String SPIN_END = FAMILY + "spin_end";
    public static final String BRACE = FAMILY + "brace";
    /**
     * All triggered animations of this brain; registered on the renegade's action controller on both sides.
     */
    public static final List<String> ACTION_ANIMATIONS = List.of(HEAVY_SWING, SHOULDER_CHECK, SLAM, LEAP, SPIN, SPIN_END, BRACE);

    private static final float MELEE_THRESHOLD = 3.0F;
    private static final float ENGAGEMENT_THRESHOLD = 16.0F;
    private static final float FLEE_THRESHOLD = 20.0F;
    private static final float DAMAGE_EXCEEDED_THRESHOLD = 8.0F;
    private static final int BRACE_DURATION = 50;
    private static final int BRACE_REARM = 100;
    private static final int BRACE_REACTION_TICKS = 10;
    private static final float LOW_HEALTH_BRACE_PCT = 0.4F;
    private static final float LAST_STAND_PCT = 0.2F;
    private static final double PREFERRED_RANGE = 2.6;
    private static final float HEAVY_SWING_RANGE = 3.8F;
    private static final int LEAP_FLIGHT_TICKS = 14;
    private static final double LEAP_LIFT = 0.588;
    private static final double LEAP_MIN_DISTANCE = 6.0;
    private static final double LEAP_MAX_DISTANCE = 14.0;
    // Highest point of the leap above the take-off height (lift 0.588 with vanilla gravity and drag), plus margin.
    private static final double LEAP_APEX = 2.4;
    // Landing may be at most this much higher or lower than the take-off.
    private static final double LEAP_MAX_HEIGHT_CHANGE = 1.5;
    // Deepest drop below the flight path that still counts as ground (deeper = abyss).
    private static final int LEAP_MAX_DROP = 3;
    private static final int LEAP_CHECK_INTERVAL = 10;
    // ANARCHY's "juggernaut/pound" ability: a cylinder of radius 6 and height 3, knockback 0.4. Damage lowered from
    // attack damage x 1.8 to x 1.0 (about one regular hit in the area).
    private static final double POUND_RADIUS = 6.0;
    private static final double POUND_HEIGHT = 3.0;
    private static final float POUND_DAMAGE = 1.0F;
    private static final double POUND_KNOCKBACK = 0.4;
    // Ticks a shield stays unusable after an axe disabled it (same as for players).
    private static final int SHIELD_DISABLE_TICKS = 100;

    private static final String SLAM_ID = "paladin_slam";
    private static final String LEAP_ID = "paladin_leap";
    private static final String SPIN_ID = "paladin_spin";
    private static final String SHOULDER_ID = "paladin_shoulder_check";
    private static final String HEAVY_SWING_ID = "paladin_heavy_swing";
    private static final String BRACE_COOLDOWN = "brace";
    private static final String BRACE_REARM_COOLDOWN = "brace_rearm";
    private static final String LEAP_CHECK_COOLDOWN = "leap_check";
    private static final String SHIELD_DISABLED_COOLDOWN = "shield_disabled";

    private boolean lowHealthBraceSpent;
    private boolean shieldBrace;
    private boolean leapLaunched;
    private final CombatAction heavySwing;
    private final CombatAction shoulderCheck;
    private final CombatAction slam;
    private final CombatAction leap;
    private final CombatAction spin;

    public PaladinBrain(RenegadeEntity entity) {
        super(entity, Stance.ADVANCING, CombatState.ADVANCING);
        this.heavySwing = CombatAction.builder(HEAVY_SWING_ID)
                .animation(HEAVY_SWING)
                .timing(14, 5, 9)
                .motion(AttackMotion.PRESSING)
                .windupDrift(-0.15)
                .advance(1.05)
                .tracking(TrackingMode.REDUCED, TrackingMode.LOCKED)
                .cooldown(68)
                .urgentRecoveryTrim(4)
                .volume(AttackVolume.sweep(SwingPath.rightToLeft(160.0F).withDynamics(SwingDynamics.FORCEFUL), HEAVY_SWING_RANGE, 2.8))
                .weaponDamage()
                .knockback(0.6, 0.12)
                .slash(SlashVisual.heavy(ParticleTypes.CRIT))
                .onStart(ctx -> ctx.entity().playSound(SoundEvents.RAVAGER_ROAR, 0.5F, 0.6F))
                .build();
        this.shoulderCheck = CombatAction.builder(SHOULDER_ID)
                .animation(SHOULDER_CHECK)
                .timing(6, 3, 8)
                .motion(AttackMotion.PRESSING)
                .advance(0.7)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(140)
                .volume(AttackVolume.cone(62.0F, 2.6))
                .scaledDamage(0.45F)
                .knockback(1.15, 0.28)
                .slash(SlashVisual.blunt(ParticleTypes.LARGE_SMOKE))
                .onActiveStart(ctx -> ctx.entity().playSound(SoundEvents.IRON_GOLEM_ATTACK, 0.9F, 0.7F))
                .build();
        this.slam = CombatAction.builder(SLAM_ID)
                .animation(SLAM)
                .timing(22, 4, 14)
                .motion(AttackMotion.PLANTED)
                .tracking(TrackingMode.REDUCED, TrackingMode.LOCKED)
                .cooldown(280)
                .urgentRecoveryTrim(6)
                .onActiveStart(ctx -> this.groundImpact())
                .build();
        // No lift here: launchLeap decides at take-off whether (and where) the paladin really jumps.
        this.leap = CombatAction.builder(LEAP_ID)
                .animation(LEAP)
                .timing(16, LEAP_FLIGHT_TICKS, 12)
                .motion(AttackMotion.COMMITTED_VECTOR)
                .committed(0.5, LEAP_FLIGHT_TICKS, 0.0, 0.0)
                .tracking(TrackingMode.REDUCED, TrackingMode.LOCKED)
                .cooldown(320)
                .urgentRecoveryTrim(4)
                .onStart(ctx -> this.leapLaunched = false)
                .onActiveStart(this::launchLeap)
                .onRecoveryStart(ctx -> {
                    if (!this.leapLaunched) {
                        return;
                    }
                    this.groundImpact();
                    LivingEntity target = ctx.liveTarget();
                    if (target != null && entity.distanceTo(target) <= 4.5F && this.cooldowns.isReady(SLAM_ID) && entity.hasLineOfSight(target)) {
                        this.chain(this.slam);
                    }
                })
                .onFinish(ctx -> this.leapLaunched = false)
                .build();
        this.spin = CombatAction.builder(SPIN_ID)
                .animation(SPIN)
                .timing(16, 62, 16)
                .motion(AttackMotion.COMMITTED_VECTOR)
                .committed(0.185, 62, 0.0, 0.028)
                .tracking(TrackingMode.MINIMAL, TrackingMode.LOCKED)
                .cooldown(920)
                .onActiveStart(ctx -> {
                    LivingEntity target = ctx.liveTarget();
                    if (target != null) {
                        ctx.locomotion().setCommittedAim(target.position());
                    }
                    this.spinPulse();
                })
                .onActiveTick(ctx -> {
                    if (ctx.phaseTick() % 8 == 0) {
                        this.spinPulse();
                    }
                    LivingEntity target = ctx.liveTarget();
                    if (target != null) {
                        ctx.locomotion().setCommittedAim(target.position());
                    }
                })
                .onRecoveryStart(ctx -> {
                    this.entity.playActionAnimation(RenegadeEntity.ACTION_CONTROLLER, SPIN_END);
                    ctx.locomotion().cancelCommitted();
                    this.spinPulse();
                })
                .build();
    }

    @Override
    protected AIStimulus createStimulus() {
        return new AIStimulus(this.entity, MELEE_THRESHOLD, ENGAGEMENT_THRESHOLD, FLEE_THRESHOLD, DAMAGE_EXCEEDED_THRESHOLD);
    }

    @Override
    protected List<Transition> stanceTransitions() {
        return List.of();
    }

    @Override
    protected List<Transition> combatStateTransitions() {
        return List.of(
                new Transition(CombatState.ADVANCING,
                        s -> RenegadesConfig.ENABLE_BRACE.get()
                                && (s.lastHitDamageExceeded && s.timeSinceLastHit <= BRACE_REACTION_TICKS
                                || s.ownHealthPct < LOW_HEALTH_BRACE_PCT && !this.lowHealthBraceSpent)
                                && this.cooldowns.isReady(BRACE_REARM_COOLDOWN)
                                && this.currentStance != Stance.LAST_STAND
                                && !this.actions.isBusy(),
                        CombatState.BRACING, 10),
                new Transition(CombatState.BRACING, s -> this.cooldowns.isReady(BRACE_COOLDOWN), CombatState.ADVANCING, 10)
        );
    }

    @Override
    protected List<PhaseTransition> definePhaseTransitions() {
        return List.of(new PhaseTransition(LAST_STAND_PCT, Stance.LAST_STAND, List.of(CombatState.BRACING), List.of(), () -> {
            this.cooldowns.clear(SLAM_ID);
            this.cooldowns.clear(SPIN_ID);
            this.cooldowns.clear(LEAP_ID);
            this.cooldowns.clear(HEAVY_SWING_ID);
            this.actions.setUrgent(true);
            this.entity.playSound(SoundEvents.RAVAGER_ROAR, 1.4F, 0.55F);
        }));
    }

    @Override
    protected void executeCurrentCombatState() {
        LivingEntity target = this.entity.getTarget();
        if (target == null) {
            return;
        }
        if (this.currentCombatState == CombatState.ADVANCING) {
            this.tickAdvancing(target);
        } else {
            this.tickBracing(target);
        }
    }

    /**
     * The heavy swing decides whether the target can be fought at all.
     */
    @Override
    protected CombatAction reachAction() {
        return this.heavySwing;
    }

    @Override
    protected void onCombatStateEnter(BrainState state) {
        if (state == CombatState.BRACING) {
            this.actions.cancel();
            this.chain(null);
            this.locomotion.arrest(MovementProfile.HARD_STOP);
            this.entity.getNavigation().stop();
            this.entity.playActionAnimation(RenegadeEntity.ACTION_CONTROLLER, BRACE);
            this.cooldowns.trigger(BRACE_COOLDOWN, BRACE_DURATION);
            if (this.stimulus.ownHealthPct < LOW_HEALTH_BRACE_PCT) {
                this.lowHealthBraceSpent = true;
            }
            if (this.hasUsableShield()) {
                this.entity.startUsingItem(InteractionHand.OFF_HAND);
                this.shieldBrace = true;
            }
        } else if (state == CombatState.ADVANCING) {
            this.cooldowns.trigger(BRACE_REARM_COOLDOWN, BRACE_REARM);
            LivingEntity target = this.entity.getTarget();
            if (target != null && this.entity.distanceTo(target) <= 4.8F) {
                this.chain(this.heavySwing);
            }
        }
    }

    @Override
    protected void onCombatStateExit(BrainState state) {
        if (state == CombatState.BRACING) {
            this.lowerShield();
        }
    }

    private void tickAdvancing(LivingEntity target) {
        if (this.actionOwnsMovement()) {
            return;
        }
        if (this.handleUnreachableTarget(target)) {
            this.chain(null);
            return;
        }
        if (this.tryChain()) {
            return;
        }

        MovementProfile profile = this.profile();
        this.locomotion.faceTarget(target, profile.turnDegrees());
        float distance = this.entity.distanceTo(target);
        if (distance <= 12.0F && this.stimulus.lineOfSightToTarget) {
            this.locomotion.setFallbackGoal(target.position(), 1.0);
            this.locomotion.pressure(target, PREFERRED_RANGE, profile);
        } else {
            this.locomotion.navigateTo(target.getX(), target.getY(), target.getZ(), 1.0);
        }

        if (this.actions.isBusy() || !this.stimulus.lineOfSightToTarget) {
            return;
        }
        if (distance >= LEAP_MIN_DISTANCE && this.canLeapTo(target)) {
            this.startAction(this.leap);
        } else if (distance <= 10.0F && distance >= 3.0F && this.cooldowns.isReady(SPIN_ID)) {
            this.startAction(this.spin);
        } else if (distance <= 3.0F && this.cooldowns.isReady(SLAM_ID)) {
            this.startAction(this.slam);
        } else if (distance <= 1.9F && this.cooldowns.isReady(SHOULDER_ID) && this.canStrike(target, this.shoulderCheck)) {
            this.startAction(this.shoulderCheck);
        } else if (distance <= HEAVY_SWING_RANGE && this.canStrike(target, this.heavySwing)) {
            this.startAction(this.heavySwing);
        }
    }

    private MovementProfile profile() {
        return this.currentStance == Stance.LAST_STAND ? MovementProfile.HEAVY_DESPERATE : MovementProfile.HEAVY_ADVANCE;
    }

    private void tickBracing(LivingEntity target) {
        this.locomotion.faceTarget(target, 6.0F);
        this.locomotion.arrest(MovementProfile.HARD_STOP);
        if (this.entity.tickCount % 4 == 0 && this.entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, this.entity.getX(), this.entity.getY() + this.entity.getBbHeight() * 0.5, this.entity.getZ(),
                    2, 0.35, 0.5, 0.35, 0.01);
        }
    }

    private boolean canLeapTo(LivingEntity target) {
        if (!RenegadesConfig.ENABLE_LEAP.get() || !this.cooldowns.isReady(LEAP_ID) || !this.cooldowns.isReady(LEAP_CHECK_COOLDOWN)) {
            return false;
        }
        this.cooldowns.trigger(LEAP_CHECK_COOLDOWN, LEAP_CHECK_INTERVAL);
        return this.isSafeLeap(target.position());
    }

    private void launchLeap(ActionContext ctx) {
        LivingEntity target = ctx.liveTarget();
        if (target == null || !this.entity.hasLineOfSight(target) || !this.isSafeLeap(target.position())) {
            // The target moved somewhere unsafe during the windup: no jump, the paladin stays where it is.
            this.locomotion.cancelCommitted();
            return;
        }

        Vec3 toDestination = GroundLocomotion.horizontal(target.position().subtract(this.entity.position()));
        double distance = Mth.clamp(toDestination.length(), 2.0, LEAP_MAX_DISTANCE);
        double speed = Math.min(1.05, distance / LEAP_FLIGHT_TICKS);
        ctx.locomotion().beginCommittedMotion(toDestination, speed, LEAP_FLIGHT_TICKS, LEAP_LIFT, 0.0);
        this.entity.playSound(SoundEvents.RAVAGER_STEP, 1.2F, 0.5F);
        this.leapLaunched = true;
    }

    /**
     * Whether a leap from here to the destination is safe: the destination is 6-14 blocks away and at most 1.5 blocks
     * higher or lower, the whole arc (body plus the jump height) is free of blocks, so the paladin neither hits a
     * ceiling nor a wall, and below every point of the path there is solid ground within 3 blocks without water or
     * lava. Checking the whole path also covers shorter landings (slowness, stopping in front of the target).
     */
    private boolean isSafeLeap(Vec3 destination) {
        Vec3 from = this.entity.position();
        Vec3 flat = GroundLocomotion.horizontal(destination.subtract(from));
        double distance = flat.length();
        double heightChange = destination.y - from.y;
        if (distance < LEAP_MIN_DISTANCE - 1.0 || distance > LEAP_MAX_DISTANCE || Math.abs(heightChange) > LEAP_MAX_HEIGHT_CHANGE) {
            return false;
        }

        Level level = this.entity.level();
        AABB body = this.entity.getBoundingBox().move(from.scale(-1.0)).deflate(0.05, 0.0, 0.05);
        int steps = Mth.ceil(distance * 2.0);
        for (int i = 1; i <= steps; i++) {
            double f = (double) i / steps;
            double arc = 4.0 * LEAP_APEX * f * (1.0 - f);
            Vec3 point = from.add(flat.scale(f)).add(0.0, heightChange * f, 0.0);
            // Body at its height on the arc, stretched down to the straight line so the climb is covered as well.
            AABB box = body.move(point).expandTowards(0.0, arc + 0.1, 0.0).move(0.0, 0.05, 0.0);
            if (!level.noCollision(this.entity, box) || !this.hasSafeGround(level, point)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasSafeGround(Level level, Vec3 point) {
        BlockPos feet = BlockPos.containing(point.x, point.y + 0.01, point.z);
        for (int depth = 0; depth <= LEAP_MAX_DROP; depth++) {
            BlockPos pos = feet.below(depth);
            if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (depth > 0 && !state.getCollisionShape(level, pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replacement for ANARCHY's "juggernaut/pound" ability: a cylindrical strike around the paladin (radius 6, height 3,
     * attack damage x 1.0, knockback 0.4) that follows the usual hit rules (only allowed targets, no hits through blocks).
     */
    private void groundImpact() {
        if (!(this.entity.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 centre = this.entity.position();
        for (LivingEntity victim : AreaStrike.victims(this.entity, centre, POUND_RADIUS, POUND_HEIGHT)) {
            if (this.entity.meleeHit(victim, POUND_DAMAGE)) {
                AreaStrike.knockAway(victim, centre, POUND_KNOCKBACK);
            }
        }

        BlockPos below = this.entity.blockPosition().below();
        BlockState ground = level.getBlockState(below);
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.entity.getX(), this.entity.getY() + 0.1, this.entity.getZ(),
                    80, POUND_RADIUS * 0.35, 0.1, POUND_RADIUS * 0.35, 0.15);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, this.entity.getX(), this.entity.getY() + 0.3, this.entity.getZ(), 3, 1.0, 0.1, 1.0, 0.0);
        this.entity.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.9F);
        this.entity.playSound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2F, 0.8F);
    }

    private void spinPulse() {
        if (!this.entity.level().isClientSide()) {
            this.strikeVolume(AttackVolume.radial(5.0), 0.6F, 0.7, SlashVisual.heavy(ParticleTypes.CRIT).withCount(40));
        }
    }

    private boolean hasUsableShield() {
        return this.entity.getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK) && this.cooldowns.isReady(SHIELD_DISABLED_COOLDOWN);
    }

    private void lowerShield() {
        if (this.shieldBrace) {
            this.shieldBrace = false;
            if (this.entity.isUsingItem()) {
                this.entity.stopUsingItem();
            }
        }
    }

    /**
     * Whether the paladin is bracing; with a shield the shield blocks (vanilla rules), without one it takes half damage.
     */
    public boolean isBracing() {
        return this.currentCombatState == CombatState.BRACING;
    }

    public boolean isShieldBrace() {
        return this.isBracing() && this.shieldBrace;
    }

    public boolean isLeaping() {
        return this.leapLaunched && this.actions.current() == this.leap;
    }

    public boolean isLastStand() {
        return this.currentStance == Stance.LAST_STAND;
    }

    /**
     * An axe hit on the raised shield (like vanilla for players): the shield is lowered and unusable for 5 seconds,
     * and the brace ends.
     */
    public void onShieldDisabled() {
        this.cooldowns.trigger(SHIELD_DISABLED_COOLDOWN, SHIELD_DISABLE_TICKS);
        this.lowerShield();
        if (this.isBracing()) {
            this.setCombatState(CombatState.ADVANCING);
        }
    }

    @Override
    public void onTargetLost() {
        this.lowerShield();
        this.setCombatState(CombatState.ADVANCING);
        super.onTargetLost();
    }

    @Override
    public String describe() {
        String brace = !this.isBracing() ? ""
                : this.shieldBrace ? (this.entity.isBlocking() ? ", bracing (shield up)" : ", bracing (raising shield)")
                : ", bracing (half damage)";
        String shield = this.cooldowns.isReady(SHIELD_DISABLED_COOLDOWN) ? "" : ", shield disabled " + this.cooldowns.remaining(SHIELD_DISABLED_COOLDOWN) + "t";
        return super.describe() + brace + shield + (this.isLastStand() ? ", LAST STAND" : "");
    }

    public enum CombatState implements BrainState {
        ADVANCING,
        BRACING;

        @Override
        public String id() {
            return this.name();
        }
    }

    public enum Stance implements BrainState {
        ADVANCING,
        LAST_STAND;

        @Override
        public String id() {
            return this.name();
        }
    }
}
