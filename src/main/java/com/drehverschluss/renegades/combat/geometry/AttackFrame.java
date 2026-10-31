// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.geometry;

import net.minecraft.world.phys.Vec3;

public record AttackFrame(Vec3 origin, float yaw, float pitch, float rollOffset, boolean mirrored, float scale) {
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);

    public Vec3 forward() {
        return Vec3.directionFromRotation(this.pitch, this.yaw);
    }

    public Vec3 right() {
        Vec3 candidate = this.forward().cross(WORLD_UP);
        if (candidate.lengthSqr() < 1.0E-6) {
            candidate = Vec3.directionFromRotation(0.0F, this.yaw).cross(WORLD_UP);
        }

        return candidate.normalize();
    }

    public Vec3 up() {
        return this.right().cross(this.forward()).normalize();
    }

    public Vec3 planeAxis(float planeRollDegrees) {
        double roll = Math.toRadians(planeRollDegrees + this.rollOffset);
        return this.right().scale(Math.cos(roll)).add(this.up().scale(Math.sin(roll))).normalize();
    }

    public Vec3 planeNormal(float planeRollDegrees) {
        double roll = Math.toRadians(planeRollDegrees + this.rollOffset);
        return this.right().scale(-Math.sin(roll)).add(this.up().scale(Math.cos(roll))).normalize();
    }

    public AttackFrame withOrigin(Vec3 origin) {
        return new AttackFrame(origin, this.yaw, this.pitch, this.rollOffset, this.mirrored, this.scale);
    }
}
