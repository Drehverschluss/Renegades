package com.drehverschluss.renegades.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Safety check for leaps (paladin leap, berserker guillotine).
 */
public final class LeapPath {
    private LeapPath() {
    }

    /**
     * Whether a leap from the mob's position to the destination is safe: the destination is within the distance range
     * and at most maxHeightChange higher or lower, the whole arc (body plus the jump height) is free of blocks, so the mob
     * neither hits a ceiling nor a wall, and below every point of the path there is solid ground within maxDrop blocks
     * without water or lava. Checking the whole path also covers shorter landings (slowness, stopping in front of the
     * target).
     */
    public static boolean isSafe(Mob mob, Vec3 destination, double minDistance, double maxDistance, double apex, double maxHeightChange,
                                 int maxDrop) {
        Vec3 from = mob.position();
        Vec3 flat = GroundLocomotion.horizontal(destination.subtract(from));
        double distance = flat.length();
        double heightChange = destination.y - from.y;
        if (distance < minDistance || distance > maxDistance || Math.abs(heightChange) > maxHeightChange) {
            return false;
        }

        Level level = mob.level();
        AABB body = mob.getBoundingBox().move(from.scale(-1.0)).deflate(0.05, 0.0, 0.05);
        int steps = Mth.ceil(distance * 2.0);
        for (int i = 1; i <= steps; i++) {
            double f = (double) i / steps;
            double arc = 4.0 * apex * f * (1.0 - f);
            Vec3 point = from.add(flat.scale(f)).add(0.0, heightChange * f, 0.0);
            // Body at its height on the arc, stretched down to the straight line so the climb is covered as well.
            AABB box = body.move(point).expandTowards(0.0, arc + 0.1, 0.0).move(0.0, 0.05, 0.0);
            if (!level.noCollision(mob, box) || !hasSafeGround(level, point, maxDrop)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasSafeGround(Level level, Vec3 point, int maxDrop) {
        BlockPos feet = BlockPos.containing(point.x, point.y + 0.01, point.z);
        for (int depth = 0; depth <= maxDrop; depth++) {
            BlockPos pos = feet.below(depth);
            if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
                return false;
            }
            if (depth > 0 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
