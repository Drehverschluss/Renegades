// Based on AbilityExecutor from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: beams need line of sight to each target; areas are AreaShape (see AbilityTargets).
package com.drehverschluss.renegades.magic;

import com.drehverschluss.renegades.magic.entity.MagicCloud;
import com.drehverschluss.renegades.magic.entity.MagicProjectile;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Casts spells: applies them directly, as an area or beam, or launches their projectile or cloud.
 */
public final class AbilityExecutor {
    private AbilityExecutor() {
    }

    /**
     * Casts the spell at the target. Returns false if a direct spell cannot reach its target.
     */
    public static boolean cast(ServerLevel level, LivingEntity caster, AbilitySpec spec, CastTarget target) {
        ImpactContext context = ImpactContext.of(caster, spec.school(), caster.position(), null);
        if (spec.delivery() instanceof Delivery.Direct && (target.entity() == null || !reaches(spec, caster, target.entity()))) {
            return false;
        }

        AbilityPresentation.release(level, caster, spec, target);
        switch (spec.delivery()) {
            case Delivery.Direct direct -> {
                LivingEntity entity = target.entity();
                applyAll(spec, context, entity);
                AbilityPresentation.impact(level, spec, entity.getBoundingBox().getCenter(), 0.0F);
            }
            case Delivery.Area area -> applyArea(level, spec, context, area, anchor(caster, area.anchor(), target), target.yaw(), null);
            case Delivery.Beam beam -> {
                Vec3 eye = caster.getEyePosition();
                Vec3 direction = beamDirection(caster, target);
                double cos = Math.cos(Math.toRadians(beam.halfAngle()));
                double rangeSq = beam.range() * beam.range();
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(beam.range()), candidate -> {
                    if (!reaches(spec, caster, candidate) || !caster.hasLineOfSight(candidate)) {
                        return false;
                    }
                    Vec3 to = candidate.getBoundingBox().getCenter().subtract(eye);
                    double lengthSq = to.lengthSqr();
                    return lengthSq <= rangeSq && (lengthSq < 1.0E-6 || to.normalize().dot(direction) >= cos);
                })) {
                    applyAll(spec, context, entity);
                }
            }
            case Delivery.Projectile projectile -> {
                Vec3 from = caster.getEyePosition();
                Vec3 aim = target.entity() != null ? target.entity().getBoundingBox().getCenter() : target.position();
                MagicProjectile.launch(level, caster, spec, projectile.spec(), context, from, aim.subtract(from), target.entity(), false);
            }
            case Delivery.Meteor meteor -> {
                Vec3 from = target.position().add(0.0, meteor.height(), 0.0);
                MagicProjectile.launch(level, caster, spec, meteor.spec(), context, from, new Vec3(0.0, -1.0, 0.0), null, true);
            }
            case Delivery.Cloud cloud -> MagicCloud.spawn(level, caster, spec, cloud.spec(), context, anchor(caster, cloud.anchor(), target), target.yaw());
        }
        return true;
    }

    /**
     * Windup effects: sound and, for area spells, the area marked on the ground.
     */
    public static void windup(ServerLevel level, LivingEntity caster, AbilitySpec spec, CastTarget target) {
        AbilityPresentation.windup(level, caster, spec, target);
    }

    /**
     * Launches the spell's projectile in a given direction (volleys, spreads).
     */
    public static MagicProjectile castProjectile(ServerLevel level, LivingEntity caster, AbilitySpec spec, CastTarget target, Vec3 direction) {
        if (!(spec.delivery() instanceof Delivery.Projectile projectile)) {
            throw new IllegalArgumentException(spec.id() + " does not deliver a projectile");
        }
        AbilityPresentation.release(level, caster, spec, target);
        ImpactContext context = ImpactContext.of(caster, spec.school(), caster.position(), null);
        return MagicProjectile.launch(level, caster, spec, projectile.spec(), context, caster.getEyePosition(), direction, target.entity(), false);
    }

    public static boolean reaches(AbilitySpec spec, LivingEntity caster, Entity entity) {
        return spec.intent() == AbilitySpec.Intent.HELPFUL ? Relations.canHelp(caster, entity) : Relations.canHarm(caster, entity);
    }

    public static void applyAll(AbilitySpec spec, ImpactContext context, LivingEntity target) {
        for (Impact impact : spec.impacts()) {
            impact.apply(context, target);
        }
    }

    /**
     * Applies the spell to everyone it reaches in the area (with line of sight from the centre), and its ally impacts
     * to the allies in it.
     */
    public static List<LivingEntity> applyArea(ServerLevel level, AbilitySpec spec, ImpactContext context, Delivery.Area area, Vec3 origin, float yaw,
                                               @Nullable Entity skip) {
        List<LivingEntity> struck = AbilityTargets.within(level, area.shape(), origin, yaw, area.height(),
                entity -> entity != skip && reaches(spec, context.caster(), entity));
        ImpactContext atOrigin = context.withOrigin(origin);
        for (LivingEntity entity : struck) {
            applyAll(spec, atOrigin, entity);
        }

        if (!spec.allyImpacts().isEmpty()) {
            LivingEntity caster = context.caster();
            for (LivingEntity ally : AbilityTargets.within(level, area.shape(), origin, yaw, area.height(),
                    entity -> entity != caster && Relations.canHelp(caster, entity))) {
                for (Impact impact : spec.allyImpacts()) {
                    impact.apply(atOrigin, ally);
                }
            }
        }
        AbilityPresentation.areaHit(level, spec, area, origin);
        return struck;
    }

    public static Vec3 beamDirection(LivingEntity caster, CastTarget target) {
        Vec3 eye = caster.getEyePosition();
        Vec3 aim = target.entity() != null ? target.entity().getBoundingBox().getCenter() : target.position();
        Vec3 direction = aim.subtract(eye);
        return direction.lengthSqr() < 1.0E-6 ? caster.getLookAngle() : direction.normalize();
    }

    static Vec3 anchor(LivingEntity caster, Delivery.Anchor anchor, CastTarget target) {
        return anchor == Delivery.Anchor.CASTER ? caster.position() : target.position();
    }
}
