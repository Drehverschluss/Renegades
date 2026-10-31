// Based on ImpactContext from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.magic;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Everything an impact needs: who cast it, which school, where it happened (knockback origin), the direct source
 * (projectile, cloud) and the caster's power for this school, fixed at the moment of casting.
 */
public record ImpactContext(LivingEntity caster, MagicSchool school, Vec3 origin, @Nullable Entity direct, double basePower, float scale) {
    public static ImpactContext of(LivingEntity caster, MagicSchool school, Vec3 origin, @Nullable Entity direct) {
        return new ImpactContext(caster, school, origin, direct, MagicBackends.basePower(caster, school), 1.0F);
    }

    public ImpactContext withOrigin(Vec3 newOrigin) {
        return new ImpactContext(this.caster, this.school, newOrigin, this.direct, this.basePower, this.scale);
    }

    public ImpactContext withDirect(@Nullable Entity newDirect) {
        return new ImpactContext(this.caster, this.school, this.origin, newDirect, this.basePower, this.scale);
    }

    public ImpactContext withBasePower(double newBasePower) {
        return new ImpactContext(this.caster, this.school, this.origin, this.direct, newBasePower, this.scale);
    }

    public ImpactContext withScale(float newScale) {
        return new ImpactContext(this.caster, this.school, this.origin, this.direct, this.basePower, newScale);
    }
}
