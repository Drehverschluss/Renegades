// Based on CastTarget from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.magic;

import com.drehverschluss.renegades.combat.ActionContext;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Where a spell is aimed: an entity (optional), a position and a facing yaw.
 */
public record CastTarget(@Nullable LivingEntity entity, Vec3 position, float yaw) {
    public static CastTarget of(ActionContext context) {
        LivingEntity target = context.target();
        Vec3 position = context.committedTargetPos() != null ? context.committedTargetPos() : (target != null ? target.position() : context.committedOrigin());
        return new CastTarget(target, position, yawOf(context.committedForward()));
    }

    public static CastTarget at(LivingEntity caster, LivingEntity target) {
        return new CastTarget(target, target.position(), yawOf(target.position().subtract(caster.position())));
    }

    public static CastTarget at(LivingEntity caster, Vec3 position) {
        return new CastTarget(null, position, yawOf(position.subtract(caster.position())));
    }

    public static float yawOf(Vec3 direction) {
        return direction.horizontalDistanceSqr() < 1.0E-8 ? 0.0F : (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }
}
