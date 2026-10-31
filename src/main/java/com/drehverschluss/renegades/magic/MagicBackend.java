// Based on MagicBackend from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.magic;

import javax.annotation.Nullable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Where spell power and spell damage come from: plain attributes (VanillaMagicBackend) or the Spell Power API.
 */
public interface MagicBackend {
    /**
     * Short name for debug output.
     */
    String name();

    /**
     * The caster's power for a school at the moment of casting (the value spell coefficients multiply).
     */
    double basePower(LivingEntity caster, MagicSchool school);

    /**
     * Final amount for one hit or heal: the base power, possibly with critical strikes and the target's
     * resistances or vulnerabilities applied.
     */
    double roll(LivingEntity caster, MagicSchool school, double base, @Nullable LivingEntity target);

    DamageSource damageSource(LivingEntity caster, MagicSchool school, @Nullable Entity direct);
}
