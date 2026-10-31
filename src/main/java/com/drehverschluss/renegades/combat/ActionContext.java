// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat;

import com.drehverschluss.renegades.entity.RenegadeEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class ActionContext {
    private final RenegadeEntity entity;
    private final GroundLocomotion locomotion;
    @Nullable
    private final LivingEntity target;
    private final Vec3 committedForward;
    private final Vec3 committedOrigin;
    @Nullable
    private final Vec3 committedTargetPos;
    @Nullable
    private SwingExecution swing;
    private AttackPhase phase = AttackPhase.WINDUP;
    private int phaseTick;
    private int scratch;

    ActionContext(
        RenegadeEntity entity,
        GroundLocomotion locomotion,
        @Nullable LivingEntity target,
        Vec3 committedForward,
        Vec3 committedOrigin,
        @Nullable Vec3 committedTargetPos
    ) {
        this.entity = entity;
        this.locomotion = locomotion;
        this.target = target;
        this.committedForward = committedForward;
        this.committedOrigin = committedOrigin;
        this.committedTargetPos = committedTargetPos;
    }

    public RenegadeEntity entity() {
        return this.entity;
    }

    public GroundLocomotion locomotion() {
        return this.locomotion;
    }

    @Nullable
    public LivingEntity target() {
        return this.target != null && this.target.isAlive() ? this.target : null;
    }

    @Nullable
    public LivingEntity liveTarget() {
        LivingEntity current = this.entity.getTarget();
        return current != null && current.isAlive() ? current : this.target();
    }

    public Vec3 committedForward() {
        return this.committedForward;
    }

    public Vec3 committedOrigin() {
        return this.committedOrigin;
    }

    @Nullable
    public Vec3 committedTargetPos() {
        return this.committedTargetPos;
    }

    public AttackPhase phase() {
        return this.phase;
    }

    public int phaseTick() {
        return this.phaseTick;
    }

    public int scratch() {
        return this.scratch;
    }

    public void setScratch(int value) {
        this.scratch = value;
    }

    @Nullable
    public SwingExecution swing() {
        return this.swing;
    }

    void setSwing(@Nullable SwingExecution swing) {
        this.swing = swing;
    }

    void setPhase(AttackPhase phase) {
        this.phase = phase;
    }

    void setPhaseTick(int tick) {
        this.phaseTick = tick;
    }
}
