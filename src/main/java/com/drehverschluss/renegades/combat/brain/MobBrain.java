// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: works on RenegadeEntity; area helpers deal their hits through RenegadeEntity#meleeHit and need line of sight.
package com.drehverschluss.renegades.combat.brain;

import com.drehverschluss.renegades.combat.ActionRunner;
import com.drehverschluss.renegades.combat.AttackPhase;
import com.drehverschluss.renegades.combat.CombatAction;
import com.drehverschluss.renegades.combat.CombatDebug;
import com.drehverschluss.renegades.combat.GroundLocomotion;
import com.drehverschluss.renegades.combat.MovementProfile;
import com.drehverschluss.renegades.combat.SlashVisual;
import com.drehverschluss.renegades.combat.TrackingMode;
import com.drehverschluss.renegades.combat.geometry.AttackFrame;
import com.drehverschluss.renegades.combat.geometry.AttackGeometry;
import com.drehverschluss.renegades.combat.geometry.AttackVolume;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Class-neutral combat brain: a stance layer and a combat state layer with transitions, plus the action runner and
 * ground locomotion. Concrete classes (warrior, rogue, ...) define their actions and decisions.
 */
public abstract class MobBrain {
    // Safety margin (blocks) for the vertical reach check, so a swing is only started when it clearly connects.
    private static final double REACH_MARGIN = 0.4;
    private static final int PATH_CHECK_INTERVAL = 20;
    private static final double HOLD_DISTANCE = 4.0;
    // Ticks before the circling direction may change while holding at an unreachable target.
    private static final int ORBIT_FLIP_INTERVAL = 60;
    protected final RenegadeEntity entity;
    public final AIStimulus stimulus;
    public final AbilityCooldownRegistry cooldowns = new AbilityCooldownRegistry();
    public final GroundLocomotion locomotion;
    public final ActionRunner actions;
    protected BrainState currentStance;
    protected BrainState currentCombatState;
    @Nullable
    private CombatAction chainNext;
    private int stanceEvalCountdown = 0;
    private int combatStateEvalCountdown = 0;
    private final Set<PhaseTransition> firedTransitions = new HashSet<>();
    private List<PhaseTransition> phaseTransitions;
    private boolean unreachable;
    private boolean pathReachesTarget;
    private int pathCheckCountdown;
    private int orbitDirection = 1;
    private int orbitFlipCountdown;

    protected MobBrain(RenegadeEntity entity, BrainState initialStance, BrainState initialCombatState) {
        this.entity = entity;
        this.currentStance = initialStance;
        this.currentCombatState = initialCombatState;
        this.stimulus = this.createStimulus();
        this.locomotion = new GroundLocomotion(entity);
        this.actions = new ActionRunner(entity, this.locomotion);
    }

    protected abstract AIStimulus createStimulus();

    protected abstract List<Transition> stanceTransitions();

    protected abstract List<Transition> combatStateTransitions();

    protected abstract List<PhaseTransition> definePhaseTransitions();

    protected abstract void executeCurrentCombatState();

    public void tick() {
        if (!this.entity.level().isClientSide()) {
            this.cooldowns.tick();
            this.stimulus.refresh();
            this.checkPhaseTransitions();
            this.locomotion.beginTick();
            this.actions.tick();
            this.entity.setPerforming(this.actions.isBusy());
            this.applyActionTracking();
            if (--this.stanceEvalCountdown <= 0) {
                this.stanceEvalCountdown = 10;
                this.evaluateLayer(this.stanceTransitions(), true);
            }

            if (--this.combatStateEvalCountdown <= 0) {
                this.combatStateEvalCountdown = 2;
                this.evaluateLayer(this.combatStateTransitions(), false);
            }

            this.executeCurrentCombatState();
            this.locomotion.endTick();
            CombatDebug.verify(this.entity, this.locomotion, this.actions);
        }
    }

    private void applyActionTracking() {
        if (this.actions.isBusy()) {
            LivingEntity target = this.entity.getTarget();
            if (target != null) {
                TrackingMode tracking = this.actions.tracking();
                if (tracking != TrackingMode.LOCKED) {
                    this.locomotion.faceTarget(target, tracking.degreesPerTick());
                }
            }
        }
    }

    protected boolean startAction(CombatAction action) {
        return this.startAction(action, 1.0);
    }

    protected boolean startAction(CombatAction action, double cooldownScale) {
        if (!this.actions.isBusy() && this.cooldowns.isReady(action.id())) {
            CombatDebug.verifyAction(action);
            if (!this.actions.start(action, this.entity.getTarget())) {
                return false;
            }

            int base = (int) (action.cooldownTicks() * cooldownScale);
            this.cooldowns.trigger(action.id(), Math.max(1, base - (int) (base * this.entity.getCooldownCoeff() * 0.4)));
            this.onActionStarted(action);
            return true;
        } else {
            return false;
        }
    }

    protected boolean forceAction(CombatAction action) {
        if (this.actions.isBusy()) {
            return false;
        }

        CombatDebug.verifyAction(action);
        if (!this.actions.start(action, this.entity.getTarget())) {
            return false;
        }

        this.cooldowns.trigger(action.id(), Math.max(1, action.cooldownTicks()));
        this.onActionStarted(action);
        return true;
    }

    protected void chain(@Nullable CombatAction action) {
        this.chainNext = action;
    }

    protected boolean tryChain() {
        if (this.chainNext != null && !this.actions.isBusy()) {
            CombatAction next = this.chainNext;
            this.chainNext = null;
            return this.forceAction(next);
        } else {
            return false;
        }
    }

    protected boolean hasChain() {
        return this.chainNext != null;
    }

    protected boolean actionOwnsMovement() {
        return this.actions.ownsMovement();
    }

    protected boolean inRecovery() {
        return this.actions.phase() == AttackPhase.RECOVERY;
    }

    protected void onActionStarted(CombatAction action) {
        this.entity.setPerforming(true);
    }

    public void onKnockback() {
        this.locomotion.notifyExternalImpulse(4);
    }

    private void evaluateLayer(List<Transition> transitions, boolean isStance) {
        BrainState current = isStance ? this.currentStance : this.currentCombatState;
        transitions.stream()
                .filter(t -> t.from() == null || t.from().equals(current))
                .filter(t -> !this.isLockedOut(t.to()))
                .filter(t -> t.condition().test(this.stimulus))
                .max(Comparator.comparingInt(Transition::priority))
                .ifPresent(t -> {
                    if (isStance) {
                        this.setStance(t.to());
                    } else {
                        this.setCombatState(t.to());
                    }
                });
    }

    private boolean isLockedOut(BrainState state) {
        for (PhaseTransition pt : this.firedTransitions) {
            if (pt.lockedOutStates().contains(state)) {
                return true;
            }
        }

        return false;
    }

    private void checkPhaseTransitions() {
        float hpPct = this.entity.getHealth() / Math.max(1.0F, this.entity.getMaxHealth());
        if (this.phaseTransitions == null) {
            this.phaseTransitions = List.copyOf(this.definePhaseTransitions());
        }

        for (PhaseTransition pt : this.phaseTransitions) {
            if (!this.firedTransitions.contains(pt) && pt.isTriggered(hpPct)) {
                this.firedTransitions.add(pt);
                this.setStance(pt.forcedStance());
                if (pt.onEnter() != null) {
                    pt.onEnter().run();
                }
                break;
            }
        }
    }

    protected void setStance(BrainState newStance) {
        if (!newStance.equals(this.currentStance)) {
            this.onStanceExit(this.currentStance);
            this.currentStance = newStance;
            this.onStanceEnter(newStance);
        }
    }

    protected void setCombatState(BrainState newState) {
        if (!newState.equals(this.currentCombatState)) {
            this.onCombatStateExit(this.currentCombatState);
            this.currentCombatState = newState;
            this.onCombatStateEnter(newState);
        }
    }

    protected void onStanceEnter(BrainState stance) {
    }

    protected void onStanceExit(BrainState stance) {
    }

    protected void onCombatStateEnter(BrainState state) {
    }

    protected void onCombatStateExit(BrainState state) {
    }

    public void onTargetLost() {
        this.actions.cancel();
        this.locomotion.cancelCommitted();
        this.locomotion.releaseToNavigation();
        this.chain(null);
        this.unreachable = false;
        this.entity.setPerforming(false);
    }

    public void onDamageTaken(float amount) {
        this.stimulus.notifyDamage(amount);
    }

    public BrainState getCurrentStance() {
        return this.currentStance;
    }

    /**
     * Whether the target's height lies within the attack volume of the action, with a safety margin: a horizontal
     * volume spans offsetVertical +- verticalExtent / 2 above the attacker's feet, and it hits a target whose centre
     * is within that band plus half the target's height.
     */
    public static boolean withinVerticalReach(LivingEntity attacker, LivingEntity target, AttackVolume volume) {
        double halfTarget = target.getBbHeight() * 0.5;
        double offPlane = target.getY() + halfTarget - (attacker.getY() + volume.offsetVertical());
        return Math.abs(offPlane) <= volume.verticalExtent() * 0.5 + halfTarget - REACH_MARGIN;
    }

    /**
     * Whether the action can reach the target from here: free line of sight and the target's height within the
     * action's attack volume. Actions without a volume only need line of sight.
     */
    protected boolean canStrike(LivingEntity target, CombatAction action) {
        if (!this.stimulus.lineOfSightToTarget) {
            return false;
        }
        AttackVolume volume = action.volume();
        return volume == null || withinVerticalReach(this.entity, target, volume);
    }

    /**
     * The attack that decides whether the target can be fought at all (usually the class's main melee attack);
     * null if the class has no height limit.
     */
    @Nullable
    protected CombatAction reachAction() {
        return null;
    }

    /**
     * If the target is out of vertical reach of the main attack (e.g. on a pillar or a ledge), handles movement and
     * returns true: walks there with normal pathfinding if a path reaches the target, otherwise keeps 3-5 blocks
     * distance and circles in combat stance until the target becomes reachable again. No attack is started meanwhile.
     */
    protected boolean handleUnreachableTarget(LivingEntity target) {
        CombatAction reach = this.reachAction();
        if (reach == null || reach.volume() == null || withinVerticalReach(this.entity, target, reach.volume())) {
            this.unreachable = false;
            return false;
        }

        if (!this.unreachable || --this.pathCheckCountdown <= 0) {
            this.pathCheckCountdown = PATH_CHECK_INTERVAL;
            Path path = this.entity.getNavigation().createPath(target, 0);
            this.pathReachesTarget = path != null && path.canReach();
        }
        this.unreachable = true;

        if (this.pathReachesTarget) {
            this.locomotion.navigateTo(target.getX(), target.getY(), target.getZ(), 1.0);
        } else {
            this.locomotion.setFallbackGoal(null, 1.0);
            if (--this.orbitFlipCountdown <= 0) {
                this.orbitFlipCountdown = ORBIT_FLIP_INTERVAL + this.entity.getRandom().nextInt(ORBIT_FLIP_INTERVAL);
                this.orbitDirection = -this.orbitDirection;
            }
            this.locomotion.faceTarget(target, MovementProfile.DISCIPLINED.turnDegrees());
            this.locomotion.orbit(target, HOLD_DISTANCE, this.orbitDirection, MovementProfile.DISCIPLINED);
        }
        return true;
    }

    public boolean isTargetUnreachable() {
        return this.unreachable;
    }

    public BrainState getCurrentCombatState() {
        return this.currentCombatState;
    }

    /**
     * Short state summary for debug output: stance, combat state and the running action with its phase.
     */
    public String describe() {
        CombatAction action = this.actions.current();
        return "stance " + this.currentStance.id() + ", state " + this.currentCombatState.id()
                + (this.unreachable ? ", target unreachable (" + (this.pathReachesTarget ? "pathing" : "holding") + ")" : "")
                + ", action " + (action == null ? "none" : action.id() + " " + this.actions.phase().name().toLowerCase() + " t" + this.actions.phaseTick())
                + String.format(java.util.Locale.ROOT, ", speed x%.2f", this.locomotion.speedFactor());
    }

    protected void performArcDamage(LivingEntity primaryTarget, float halfAngleDeg, float range) {
        this.performArcDamage(primaryTarget, halfAngleDeg, range, this.entity.getLookAngle(), 1.0F);
    }

    protected void performArcDamage(LivingEntity primaryTarget, float halfAngleDeg, float range, Vec3 forward, float damageScale) {
        if (!this.entity.level().isClientSide()) {
            double cosHalfAngle = Math.cos(Math.toRadians(halfAngleDeg));
            Vec3 centre = forward.horizontalDistanceSqr() < 1.0E-6 ? this.entity.getLookAngle() : new Vec3(forward.x, 0.0, forward.z).normalize();
            this.entity
                    .level()
                    .getEntities(
                            EntityTypeTest.forClass(LivingEntity.class),
                            this.entity.getBoundingBox().inflate(range),
                            t -> t != this.entity
                                    && t != primaryTarget
                                    && this.entity.canHarm(t)
                                    && this.entity.hasLineOfSight(t)
                                    && this.entity.distanceTo(t) <= range
                                    && centre.dot(t.position().subtract(this.entity.position()).normalize()) >= cosHalfAngle
                    )
                    .forEach(arcTarget -> this.entity.meleeHit(arcTarget, damageScale));
        }
    }

    protected void strikeVolume(AttackVolume volume, float damageScale, double knockback, @Nullable SlashVisual slash) {
        if (this.entity.level() instanceof ServerLevel world) {
            AttackFrame frame = AttackGeometry.capture(volume, this.entity, false, 0.0F);
            AABB bounds = AttackGeometry.bounds(volume, frame, null);

            for (LivingEntity victim : world.getEntitiesOfClass(LivingEntity.class, bounds, t -> this.entity.canHarm(t) && this.entity.hasLineOfSight(t))) {
                if (AttackGeometry.hits(volume, frame, 0.0F, 1.0F, victim.getBoundingBox(), null)
                        && this.entity.meleeHit(victim, damageScale)
                        && knockback > 0.0) {
                    Vec3 away = victim.position().subtract(frame.origin());
                    if (away.horizontalDistanceSqr() > 1.0E-6) {
                        away = new Vec3(away.x, 0.0, away.z).normalize();
                        victim.knockback(knockback, -away.x, -away.z);
                    }
                }
            }

            if (slash != null && slash.perTick() > 0) {
                RandomSource random = this.entity.getRandom();

                for (int i = 0; i < slash.perTick(); i++) {
                    AttackGeometry.Sample sample = AttackGeometry.sample(volume, frame, 0.0F, 1.0F, random, slash.speed(), slash.scatter());
                    world.sendParticles(
                            slash.particle(),
                            sample.position().x,
                            sample.position().y,
                            sample.position().z,
                            0,
                            sample.velocity().x,
                            sample.velocity().y,
                            sample.velocity().z,
                            1.0
                    );
                }
            }
        }
    }

    protected void meleeStrike(LivingEntity primary, float halfAngleDeg, float range, Vec3 forward) {
        if (!this.entity.level().isClientSide()) {
            if (primary != null && this.entity.distanceTo(primary) <= range + 0.75F && this.entity.hasLineOfSight(primary)) {
                this.entity.doHurtTarget(primary);
            }

            this.performArcDamage(primary, halfAngleDeg, range, forward, 1.0F);
        }
    }
}
