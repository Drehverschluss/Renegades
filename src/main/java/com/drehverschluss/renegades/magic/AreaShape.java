package com.drehverschluss.renegades.magic;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ground shape of an area spell, placed at an origin and facing a yaw (replaces AtlasVFX's TelegraphArea). An area
 * spans from half a block below the origin up to the given height; a hitbox counts as inside when its closest point
 * lies within the shape.
 */
public sealed interface AreaShape permits AreaShape.Circle, AreaShape.Cone {
    /**
     * Horizontal reach of the shape (radius).
     */
    double extent();

    boolean contains(Vec3 origin, float yaw, AABB box, double height);

    static Circle circle(double radius) {
        return new Circle(radius);
    }

    static Cone cone(double radius, float halfAngleDegrees) {
        return new Cone(radius, halfAngleDegrees);
    }

    record Circle(double radius) implements AreaShape {
        @Override
        public double extent() {
            return this.radius;
        }

        @Override
        public boolean contains(Vec3 origin, float yaw, AABB box, double height) {
            return withinHeight(origin, box, height) && horizontalDistance(origin, box) <= this.radius;
        }
    }

    record Cone(double radius, float halfAngleDegrees) implements AreaShape {
        @Override
        public double extent() {
            return this.radius;
        }

        @Override
        public boolean contains(Vec3 origin, float yaw, AABB box, double height) {
            if (!withinHeight(origin, box, height) || horizontalDistance(origin, box) > this.radius) {
                return false;
            }
            Vec3 centre = box.getCenter();
            double dx = centre.x - origin.x;
            double dz = centre.z - origin.z;
            if (dx * dx + dz * dz < 0.25) {
                return true;
            }
            float angleTo = (float) Math.toDegrees(Math.atan2(-dx, dz));
            return Math.abs(Mth.wrapDegrees(angleTo - yaw)) <= this.halfAngleDegrees;
        }
    }

    private static boolean withinHeight(Vec3 origin, AABB box, double height) {
        return box.maxY > origin.y - 0.5 && box.minY < origin.y + height;
    }

    private static double horizontalDistance(Vec3 point, AABB box) {
        double dx = Math.max(0.0, Math.max(box.minX - point.x, point.x - box.maxX));
        double dz = Math.max(0.0, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return Math.sqrt(dx * dx + dz * dz);
    }
}
