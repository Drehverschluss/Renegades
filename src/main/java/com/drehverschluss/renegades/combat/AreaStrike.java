package com.drehverschluss.renegades.combat;

import com.drehverschluss.renegades.entity.RenegadeEntity;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cylindrical area hits (ground impacts, trap blasts): everything the attacker may harm (RenegadeEntity#canHarm)
 * whose hitbox reaches into a cylinder of the given radius and height standing on the centre, with no block between
 * the centre and the victim.
 */
public final class AreaStrike {
    // The cylinder reaches this far below the centre, so targets standing on a slightly lower block are still hit.
    private static final double BELOW = 0.5;

    private AreaStrike() {
    }

    public static List<LivingEntity> victims(RenegadeEntity attacker, Vec3 centre, double radius, double height) {
        AABB bounds = new AABB(centre.x - radius, centre.y - BELOW, centre.z - radius, centre.x + radius, centre.y + height, centre.z + radius);
        Vec3 eye = centre.add(0.0, Math.min(1.0, height * 0.5), 0.0);
        return attacker.level().getEntitiesOfClass(LivingEntity.class, bounds, victim -> attacker.canHarm(victim)
                && victim.getBoundingBox().minY < centre.y + height
                && horizontalDistanceToBox(centre, victim.getBoundingBox()) <= radius
                && attacker.level().clip(new ClipContext(eye, victim.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, victim)).getType() == HitResult.Type.MISS);
    }

    /**
     * Pushes the victim horizontally away from the centre.
     */
    public static void knockAway(LivingEntity victim, Vec3 centre, double strength) {
        Vec3 away = GroundLocomotion.horizontal(victim.position().subtract(centre));
        if (strength > 0.0 && away.lengthSqr() > 1.0E-6) {
            away = away.normalize();
            victim.knockback(strength, -away.x, -away.z);
        }
    }

    private static double horizontalDistanceToBox(Vec3 point, AABB box) {
        double dx = Math.max(0.0, Math.max(box.minX - point.x, point.x - box.maxX));
        double dz = Math.max(0.0, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return Math.sqrt(dx * dx + dz * dz);
    }
}
