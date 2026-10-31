// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.geometry;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class AttackGeometry {
    private static final double EPSILON = 1.0E-6;

    private AttackGeometry() {
    }

    public static AttackFrame capture(AttackVolume volume, LivingEntity attacker, boolean mirrored, float rollOffset) {
        SwingPath swing = volume.swing();
        float yaw = Mth.wrapDegrees(attacker.getYRot() + swing.yawOffset());
        float aim = attacker instanceof AttackAim.Aiming aiming && AttackAim.appliesTo(volume) ? aiming.attackPitch() : 0.0F;
        float pitch = Mth.wrapDegrees(aim + swing.pitchOffset());
        float scale = attacker.getAgeScale();
        AttackFrame base = new AttackFrame(attacker.position(), yaw, pitch, rollOffset, mirrored, scale);
        double lateral = volume.offsetLateral() * swing.handedness().lateralSign() * (mirrored ? -1.0 : 1.0);
        Vec3 origin = attacker.position()
            .add(base.forward().scale(volume.offsetForward() * scale))
            .add(base.right().scale(lateral * scale))
            .add(0.0, volume.offsetVertical() * scale, 0.0);
        return base.withOrigin(origin);
    }

    public static Vec3 surfacePoint(AttackVolume volume, AttackFrame frame, float s, float t) {
        Vec3 forward = frame.forward();

        return switch (volume.shape().family()) {
            case ARC -> {
                double angle = Math.toRadians(sweepAngleDegrees(volume, frame, s));
                Vec3 plane = planeAxis(volume, frame);
                Vec3 direction = forward.scale(Math.cos(angle)).add(plane.scale(Math.sin(angle)));
                yield frame.origin().add(direction.scale(radiusAt(volume, frame, t)));
            }
            case LANE -> frame.origin()
                .add(forward.scale((volume.innerRadius() + (volume.range() - volume.innerRadius()) * laneExtension(volume, s) * t) * frame.scale()));
            case PATH -> frame.origin().add(forward.scale(volume.range() * 0.5 * frame.scale() * (t - 1.0)));
            case RADIAL -> {
                double angle = Math.toRadians(s * 360.0);
                Vec3 plane = planeAxis(volume, frame);
                Vec3 direction = forward.scale(Math.cos(angle)).add(plane.scale(Math.sin(angle)));
                yield frame.origin().add(direction.scale(radiusAt(volume, frame, t)));
            }
        };
    }

    public static float sweepAngleDegrees(AttackVolume volume, AttackFrame frame, float s) {
        float angle = volume.swing().angleAt(s);
        return frame.mirrored() ? -angle : angle;
    }

    public static Vec3 strikeDirection(AttackVolume volume, AttackFrame frame, float s) {
        return switch (volume.shape().family()) {
            case ARC -> {
                double angle = Math.toRadians(sweepAngleDegrees(volume, frame, s));
                Vec3 plane = planeAxis(volume, frame);
                Vec3 tangent = frame.forward().scale(-Math.sin(angle)).add(plane.scale(Math.cos(angle)));
                boolean forwardTravel = volume.swing().travelsPositive() != frame.mirrored();
                yield forwardTravel ? tangent : tangent.scale(-1.0);
            }
            case LANE, PATH, RADIAL -> frame.forward();
        };
    }

    public static double knockbackAlignment(AttackVolume volume) {
        return switch (volume.shape().family()) {
            case ARC -> 0.55;
            case LANE, PATH -> 0.75;
            case RADIAL -> 0.0;
        };
    }

    public static float laneExtension(AttackVolume volume, float s) {
        return volume.swing().dynamics().apply(s);
    }

    public static double radiusAt(AttackVolume volume, AttackFrame frame, float t) {
        return (volume.innerRadius() + (volume.range() - volume.innerRadius()) * Mth.clamp(t, 0.0F, 1.0F)) * frame.scale();
    }

    public static double rangeOf(AttackVolume volume, AttackFrame frame) {
        return volume.range() * frame.scale();
    }

    public static double widthOf(AttackVolume volume, AttackFrame frame) {
        return volume.width() * frame.scale();
    }

    public static double halfThickness(AttackVolume volume, AttackFrame frame) {
        return volume.verticalExtent() * 0.5 * frame.scale();
    }

    public static Vec3 planeAxis(AttackVolume volume, AttackFrame frame) {
        float roll = volume.planeRoll();
        return frame.planeAxis(frame.mirrored() ? -roll : roll);
    }

    public static Vec3 planeNormal(AttackVolume volume, AttackFrame frame) {
        float roll = volume.planeRoll();
        return frame.planeNormal(frame.mirrored() ? -roll : roll);
    }

    public static AABB bounds(AttackVolume volume, AttackFrame frame, @Nullable Vec3 previousOrigin) {
        double pad = Math.max(volume.width(), volume.verticalExtent()) * 0.5 * frame.scale() + 1.0;

        return switch (volume.shape().family()) {
            case ARC, RADIAL -> {
                double reach = rangeOf(volume, frame) * 2.0 + pad;
                yield AABB.ofSize(frame.origin(), reach, reach, reach);
            }
            case LANE -> {
                Vec3 tip = frame.origin().add(frame.forward().scale(rangeOf(volume, frame)));
                yield new AABB(frame.origin(), tip).inflate(pad);
            }
            case PATH -> {
                Vec3 from = previousOrigin != null ? previousOrigin : frame.origin();
                yield new AABB(from, frame.origin()).inflate(pad);
            }
        };
    }

    public static boolean hits(AttackVolume volume, AttackFrame frame, float s0, float s1, AABB victimBox, @Nullable Vec3 previousOrigin) {
        return switch (volume.shape().family()) {
            case ARC -> hitsArc(volume, frame, s0, s1, victimBox);
            case LANE -> hitsLane(volume, frame, s1, victimBox);
            case PATH -> hitsPath(volume, frame, victimBox, previousOrigin);
            case RADIAL -> hitsRadial(volume, frame, victimBox);
        };
    }

    private static boolean hitsArc(AttackVolume volume, AttackFrame frame, float s0, float s1, AABB victim) {
        Vec3 forward = frame.forward();
        Vec3 plane = planeAxis(volume, frame);
        Vec3 normal = planeNormal(volume, frame);
        Vec3 toVictim = victim.getCenter().subtract(frame.origin());
        double along = toVictim.dot(forward);
        double lateral = toVictim.dot(plane);
        double offPlane = toVictim.dot(normal);
        if (Math.abs(offPlane) > halfThickness(volume, frame) + boxExtent(victim, normal)) {
            return false;
        } else {
            double radius = Math.sqrt(along * along + lateral * lateral);
            if (radius < 1.0E-6) {
                return volume.innerRadius() <= 0.5;
            } else {
                Vec3 radialDir = forward.scale(along / radius).add(plane.scale(lateral / radius));
                double radialPad = boxExtent(victim, radialDir);
                if (!(radius > rangeOf(volume, frame) + radialPad) && !(radius < volume.innerRadius() * frame.scale() - radialPad)) {
                    Vec3 tangent = forward.scale(-lateral / radius).add(plane.scale(along / radius));
                    float angularPad = (float)Math.toDegrees(Math.atan2(boxExtent(victim, tangent), radius));
                    float angle = (float)Math.toDegrees(Math.atan2(lateral, along));
                    float a0 = sweepAngleDegrees(volume, frame, s0);
                    float a1 = sweepAngleDegrees(volume, frame, s1);
                    float lowest = Math.min(a0, a1) - angularPad;
                    float highest = Math.max(a0, a1) + angularPad;
                    return angle >= lowest && angle <= highest;
                } else {
                    return false;
                }
            }
        }
    }

    private static boolean hitsLane(AttackVolume volume, AttackFrame frame, float extension, AABB victim) {
        Vec3 forward = frame.forward();
        Vec3 plane = planeAxis(volume, frame);
        Vec3 normal = planeNormal(volume, frame);
        Vec3 toVictim = victim.getCenter().subtract(frame.origin());
        double along = toVictim.dot(forward);
        double tip = radiusAt(volume, frame, laneExtension(volume, extension));
        if (!(along < volume.innerRadius() * frame.scale() - boxExtent(victim, forward)) && !(along > tip + boxExtent(victim, forward))) {
            return Math.abs(toVictim.dot(plane)) > widthOf(volume, frame) * 0.5 + boxExtent(victim, plane)
                ? false
                : Math.abs(toVictim.dot(normal)) <= halfThickness(volume, frame) + boxExtent(victim, normal);
        } else {
            return false;
        }
    }

    private static boolean hitsPath(AttackVolume volume, AttackFrame frame, AABB victim, @Nullable Vec3 previousOrigin) {
        Vec3 to = frame.origin();
        Vec3 from = previousOrigin != null ? previousOrigin : to;
        AABB padded = victim.inflate(widthOf(volume, frame) * 0.5, halfThickness(volume, frame), widthOf(volume, frame) * 0.5);
        return padded.contains(to) || padded.contains(from) || padded.clip(from, to).isPresent();
    }

    private static boolean hitsRadial(AttackVolume volume, AttackFrame frame, AABB victim) {
        Vec3 closest = closestPointOnBox(victim, frame.origin());
        double reach = rangeOf(volume, frame);
        return closest.distanceToSqr(frame.origin()) <= reach * reach;
    }

    private static double boxExtent(AABB box, Vec3 axis) {
        double hx = (box.maxX - box.minX) * 0.5;
        double hy = (box.maxY - box.minY) * 0.5;
        double hz = (box.maxZ - box.minZ) * 0.5;
        return Math.abs(axis.x) * hx + Math.abs(axis.y) * hy + Math.abs(axis.z) * hz;
    }

    private static Vec3 closestPointOnBox(AABB box, Vec3 point) {
        return new Vec3(Mth.clamp(point.x, box.minX, box.maxX), Mth.clamp(point.y, box.minY, box.maxY), Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    public static AttackGeometry.Sample sample(AttackVolume volume, AttackFrame frame, float s0, float s1, RandomSource random, double speed, double scatter) {
        float t = (float)Math.sqrt(random.nextDouble());
        float s = Mth.lerp(random.nextFloat(), s0, s1);
        Vec3 position = surfacePoint(volume, frame, s, t);
        Vec3 outward = outwardDirection(volume, frame, s);
        Vec3 normal = planeNormal(volume, frame);
        Vec3 plane = planeAxis(volume, frame);
        double thickness = halfThickness(volume, frame);
        position = position.add(normal.scale((random.nextDouble() * 2.0 - 1.0) * thickness * 0.7));
        if (volume.shape().family() == AttackShape.Family.LANE || volume.shape().family() == AttackShape.Family.PATH) {
            position = position.add(plane.scale((random.nextDouble() * 2.0 - 1.0) * widthOf(volume, frame) * 0.45));
        }

        if (scatter > 0.0) {
            position = position.add(
                (random.nextDouble() * 2.0 - 1.0) * scatter, (random.nextDouble() * 2.0 - 1.0) * scatter, (random.nextDouble() * 2.0 - 1.0) * scatter
            );
        }

        return new AttackGeometry.Sample(position, outward.scale(speed), t);
    }

    public static Vec3 outwardDirection(AttackVolume volume, AttackFrame frame, float s) {
        return switch (volume.shape().family()) {
            case ARC -> {
                double angle = Math.toRadians(sweepAngleDegrees(volume, frame, s));
                yield frame.forward().scale(Math.cos(angle)).add(planeAxis(volume, frame).scale(Math.sin(angle)));
            }
            case LANE, PATH -> frame.forward();
            case RADIAL -> {
                double angle = Math.toRadians(s * 360.0);
                yield frame.forward().scale(Math.cos(angle)).add(planeAxis(volume, frame).scale(Math.sin(angle)));
            }
        };
    }

    public record Sample(Vec3 position, Vec3 velocity, float bladeFraction) {
    }
}
