// Based on AbilityTargets from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: area hits need line of sight from the centre, so they never reach through walls.
package com.drehverschluss.renegades.magic;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AbilityTargets {
    private AbilityTargets() {
    }

    /**
     * Living entities inside the area that pass the filter and can be seen from a point one block above the area's
     * origin (no hits through walls).
     */
    public static List<LivingEntity> within(ServerLevel level, AreaShape shape, Vec3 origin, float yaw, double height, Predicate<LivingEntity> filter) {
        double extent = shape.extent();
        AABB search = new AABB(origin.x - extent, origin.y - 0.5, origin.z - extent, origin.x + extent, origin.y + height, origin.z + extent).inflate(1.0, 0.0, 1.0);
        Vec3 eye = origin.add(0.0, Math.min(1.0, height * 0.5), 0.0);
        return level.getEntitiesOfClass(LivingEntity.class, search, entity -> filter.test(entity)
                && shape.contains(origin, yaw, entity.getBoundingBox(), height)
                && level.clip(new ClipContext(eye, entity.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity))
                .getType() == HitResult.Type.MISS);
    }

    public static List<LivingEntity> harmable(ServerLevel level, LivingEntity caster, AreaShape shape, Vec3 origin, float yaw, double height) {
        return within(level, shape, origin, yaw, height, entity -> Relations.canHarm(caster, entity));
    }
}
