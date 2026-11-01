// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: no spell casts; animations are GeckoLib triggered animations on the renegade (synced to clients by GeckoLib).
package com.drehverschluss.renegades.combat;

import com.drehverschluss.renegades.combat.geometry.AttackFrame;
import com.drehverschluss.renegades.combat.geometry.AttackGeometry;
import com.drehverschluss.renegades.combat.geometry.AttackVolume;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Runs one combat action at a time through WINDUP -> ACTIVE -> RECOVERY -> FINISHED. Damage is only dealt in ACTIVE.
 */
public final class ActionRunner {
    private final RenegadeEntity entity;
    private final GroundLocomotion locomotion;
    @Nullable
    private CombatAction current;
    @Nullable
    private ActionContext context;
    @Nullable
    private SwingExecution swing;
    private AttackPhase phase = AttackPhase.FINISHED;
    private int phaseTick;
    private int recoveryBudget;
    private boolean urgent;

    public ActionRunner(RenegadeEntity entity, GroundLocomotion locomotion) {
        this.entity = entity;
        this.locomotion = locomotion;
    }

    public boolean isBusy() {
        return this.current != null;
    }

    @Nullable
    public CombatAction current() {
        return this.current;
    }

    @Nullable
    public ActionContext context() {
        return this.context;
    }

    public AttackPhase phase() {
        return this.current == null ? AttackPhase.FINISHED : this.phase;
    }

    public int phaseTick() {
        return this.phaseTick;
    }

    public TrackingMode tracking() {
        return this.current == null ? TrackingMode.FULL : this.current.tracking(this.phase);
    }

    public boolean ownsMovement() {
        return this.current != null && this.current.motion().ownsMovement();
    }

    public void setUrgent(boolean urgent) {
        this.urgent = urgent;
    }

    public boolean start(CombatAction action, @Nullable LivingEntity target) {
        if (this.current != null) {
            return false;
        }

        Vec3 forward = this.locomotion.facing();
        if (target != null) {
            Vec3 toTarget = GroundLocomotion.horizontal(target.position().subtract(this.entity.position()));
            if (toTarget.lengthSqr() > 1.0E-4) {
                forward = toTarget.normalize();
            }
        }

        this.current = action;
        this.phase = AttackPhase.WINDUP;
        this.phaseTick = 0;
        this.recoveryBudget = Math.max(0, action.recoveryTicks() - (this.urgent ? action.urgentRecoveryTrim() : 0));
        this.context = new ActionContext(this.entity, this.locomotion, target, forward, this.entity.position(), target == null ? null : target.position());
        this.context.setPhase(AttackPhase.WINDUP);
        if (action.motion().ownsMovement()) {
            this.locomotion.claimAttackMotion();
        }

        if (action.animation() != null) {
            this.entity.playActionAnimation(action.animationController(), action.animation());
        }

        action.fireStart(this.context);
        if (action.windupTicks() <= 0) {
            this.enterActive();
        }

        return true;
    }

    public void tick() {
        CombatAction action = this.current;
        ActionContext ctx = this.context;
        if (action != null && ctx != null) {
            if (action.motion().ownsMovement()) {
                this.locomotion.claimAttackMotion();
            }

            this.phaseTick++;
            ctx.setPhaseTick(this.phaseTick);
            switch (this.phase) {
                case WINDUP:
                    this.applyWindupMotion(action, ctx);
                    if (this.phaseTick >= action.windupTicks()) {
                        this.enterActive();
                    }
                    break;
                case ACTIVE:
                    this.applyActiveMotion(action, ctx);
                    if (this.swing != null) {
                        this.swing.tick(this.phaseTick);
                    }

                    action.fireActiveTick(ctx);
                    if (this.phaseTick >= action.activeTicks()) {
                        this.enterRecovery();
                    }
                    break;
                case RECOVERY:
                    if (this.phaseTick >= this.recoveryBudget) {
                        this.finish();
                    }
                    break;
                case FINISHED:
                    this.finish();
            }
        }
    }

    public void cancel() {
        if (this.current != null) {
            if (this.current.animation() != null) {
                this.entity.stopActionAnimation(this.current.animationController(), this.current.animation());
            }

            this.current = null;
            this.context = null;
            this.swing = null;
            this.phase = AttackPhase.FINISHED;
            this.phaseTick = 0;
            this.locomotion.cancelCommitted();
            this.locomotion.releaseAttackMotion();
        }
    }

    private void enterActive() {
        CombatAction action = this.current;
        ActionContext ctx = this.context;
        if (action != null && ctx != null) {
            this.phase = AttackPhase.ACTIVE;
            this.phaseTick = 0;
            ctx.setPhase(AttackPhase.ACTIVE);
            ctx.setPhaseTick(0);
            AttackVolume volume = action.volume();
            if (volume != null) {
                AttackFrame frame = AttackGeometry.capture(volume, this.entity, false, 0.0F);
                this.swing = new SwingExecution(this.entity, action, volume, frame);
                ctx.setSwing(this.swing);
            }

            if (action.motion().isCommitted()) {
                Vec3 heading = this.committedHeading(action, ctx);
                if (action.motion() != AttackMotion.SIDESTEP) {
                    this.locomotion.snapFacing(heading);
                }
                this.locomotion
                        .beginCommittedMotion(heading, action.committedSpeed(), action.committedTicks(), action.committedLift(), action.committedCorrection());
                if (action.committedCorrection() > 0.0 && ctx.committedTargetPos() != null) {
                    this.locomotion.setCommittedAim(ctx.committedTargetPos());
                }
            }

            action.fireActiveStart(ctx);
        }
    }

    private void enterRecovery() {
        CombatAction action = this.current;
        ActionContext ctx = this.context;
        if (action != null && ctx != null) {
            this.phase = AttackPhase.RECOVERY;
            this.phaseTick = 0;
            ctx.setPhase(AttackPhase.RECOVERY);
            ctx.setPhaseTick(0);
            if (action.motion().isCommitted()) {
                this.locomotion.cancelCommitted();
            }

            action.fireRecoveryStart(ctx);
            if (this.recoveryBudget <= 0) {
                this.finish();
            }
        }
    }

    private void finish() {
        CombatAction action = this.current;
        ActionContext ctx = this.context;
        this.current = null;
        this.context = null;
        this.swing = null;
        this.phase = AttackPhase.FINISHED;
        this.phaseTick = 0;
        this.locomotion.releaseAttackMotion();
        if (action != null && ctx != null) {
            ctx.setPhase(AttackPhase.FINISHED);
            action.fireFinish(ctx);
        }
    }

    private void applyWindupMotion(CombatAction action, ActionContext ctx) {
        if (action.motion().ownsMovement()) {
            if (action.motion().arrestsOnWindup()) {
                this.locomotion.applyAttackMotion(Vec3.ZERO);
            } else if (action.windupDrift() != 0.0 && action.windupTicks() > 0) {
                double perTick = action.windupDrift() / action.windupTicks();
                this.locomotion.applyAttackMotion(ctx.committedForward().scale(perTick));
            } else {
                this.locomotion.applyAttackMotion(Vec3.ZERO);
            }
        }
    }

    private void applyActiveMotion(CombatAction action, ActionContext ctx) {
        if (action.motion().ownsMovement() && !action.motion().isCommitted()) {
            if (action.advance() != 0.0 && action.activeTicks() > 0) {
                double perTick = action.advance() / action.activeTicks();
                this.locomotion.applyAttackMotion(ctx.committedForward().scale(perTick));
            } else {
                this.locomotion.applyAttackMotion(Vec3.ZERO);
            }
        }
    }

    private Vec3 committedHeading(CombatAction action, ActionContext ctx) {
        LivingEntity target = ctx.liveTarget();

        return switch (action.motion()) {
            case CROSS_THROUGH -> target == null ? ctx.committedForward() : this.locomotion.crossVector(target, ctx.scratch() >= 0 ? 1 : -1);
            case RETREATING -> target == null ? ctx.committedForward().scale(-1.0) : this.locomotion.retreatVector(target);
            case SIDESTEP -> {
                Vec3 forward = ctx.committedForward();
                yield new Vec3(-forward.z, 0.0, forward.x).scale(ctx.scratch() >= 0 ? 1.0 : -1.0);
            }
            default -> ctx.committedForward();
        };
    }
}
