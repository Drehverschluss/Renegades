// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.geometry;

import net.minecraft.util.Mth;

public record SwingPath(
    AttackPlane plane, float startAngle, float endAngle, float planeRoll, float yawOffset, float pitchOffset, Handedness handedness, SwingDynamics dynamics
) {
    public SwingPath(AttackPlane plane, float startAngle, float endAngle, float planeRoll, float yawOffset, float pitchOffset, Handedness handedness) {
        this(plane, startAngle, endAngle, planeRoll, yawOffset, pitchOffset, handedness, SwingDynamics.STEADY);
    }

    public float angleAt(float s) {
        return Mth.lerp(this.dynamics.apply(s), this.startAngle, this.endAngle);
    }

    public float sweepDegrees() {
        return Math.abs(this.endAngle - this.startAngle);
    }

    public boolean travelsPositive() {
        return this.endAngle >= this.startAngle;
    }

    public SwingPath mirrored() {
        return new SwingPath(
            this.plane, -this.startAngle, -this.endAngle, -this.planeRoll, -this.yawOffset, this.pitchOffset, this.handedness.opposite(), this.dynamics
        );
    }

    public SwingPath withYawOffset(float degrees) {
        return new SwingPath(this.plane, this.startAngle, this.endAngle, this.planeRoll, degrees, this.pitchOffset, this.handedness, this.dynamics);
    }

    public SwingPath withPitchOffset(float degrees) {
        return new SwingPath(this.plane, this.startAngle, this.endAngle, this.planeRoll, this.yawOffset, degrees, this.handedness, this.dynamics);
    }

    public SwingPath withHandedness(Handedness handedness) {
        return new SwingPath(this.plane, this.startAngle, this.endAngle, this.planeRoll, this.yawOffset, this.pitchOffset, handedness, this.dynamics);
    }

    public SwingPath withDynamics(SwingDynamics dynamics) {
        return new SwingPath(this.plane, this.startAngle, this.endAngle, this.planeRoll, this.yawOffset, this.pitchOffset, this.handedness, dynamics);
    }

    public static SwingPath rightToLeft(float arcDegrees) {
        return new SwingPath(AttackPlane.HORIZONTAL, arcDegrees * 0.5F, -arcDegrees * 0.5F, AttackPlane.HORIZONTAL.defaultRoll(), 0.0F, 0.0F, Handedness.RIGHT);
    }

    public static SwingPath leftToRight(float arcDegrees) {
        return new SwingPath(AttackPlane.HORIZONTAL, -arcDegrees * 0.5F, arcDegrees * 0.5F, AttackPlane.HORIZONTAL.defaultRoll(), 0.0F, 0.0F, Handedness.LEFT);
    }

    public static SwingPath overhead(float arcDegrees, AttackPlane plane) {
        return new SwingPath(plane, arcDegrees * 0.5F, -arcDegrees * 0.5F, plane.defaultRoll(), 0.0F, 0.0F, Handedness.RIGHT);
    }

    public static SwingPath rising(float arcDegrees, AttackPlane plane) {
        return new SwingPath(plane, -arcDegrees * 0.5F, arcDegrees * 0.5F, plane.defaultRoll(), 0.0F, 0.0F, Handedness.RIGHT);
    }

    public static SwingPath diagonalDescending(float arcDegrees, float planeRoll) {
        return new SwingPath(AttackPlane.CUSTOM, arcDegrees * 0.5F, -arcDegrees * 0.5F, planeRoll, 0.0F, 0.0F, Handedness.RIGHT);
    }

    public static SwingPath fixedSpan(float arcDegrees) {
        return new SwingPath(AttackPlane.HORIZONTAL, -arcDegrees * 0.5F, arcDegrees * 0.5F, 0.0F, 0.0F, 0.0F, Handedness.CENTRED);
    }

    public static SwingPath lane(float planeRoll) {
        return new SwingPath(AttackPlane.CUSTOM, 0.0F, 0.0F, planeRoll, 0.0F, 0.0F, Handedness.CENTRED);
    }
}
