// Based on SpellPowerMagicBackend from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: the power is the attack damage plus the Spell Power of the school (ANARCHY used only the Spell Power,
// which is 0 for mobs without spell gear); the strength damage multiplier applies to the Spell Power part.
package com.drehverschluss.renegades.compat.spellpower;

import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.magic.MagicBackend;
import com.drehverschluss.renegades.magic.MagicSchool;
import com.drehverschluss.renegades.magic.VanillaMagicBackend;
import javax.annotation.Nullable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.spell_power.api.SpellDamageSource;
import net.spell_power.api.SpellPower;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellSchools;

/**
 * Backend with the Spell Power API. Spell Power puts its attributes on every living entity, so spell gear (wizard
 * robes, staves, wands) works on renegades exactly like on players:
 * <ul>
 * <li>power = attack damage + Spell Power of the school (from gear, generic power included) x damage multiplier, so a
 * renegade without spell gear still hits like with the vanilla backend;</li>
 * <li>every hit can crit with the caster's spell critical chance and damage;</li>
 * <li>the target's resistances and vulnerabilities for the school apply;</li>
 * <li>the damage source is Spell Power's for the school (counts as magic, works with everything that reacts to
 * spell damage).</li>
 * </ul>
 */
final class SpellPowerMagicBackend implements MagicBackend {
    static final SpellPowerMagicBackend INSTANCE = new SpellPowerMagicBackend();

    private SpellPowerMagicBackend() {
    }

    @Override
    public String name() {
        return "spell_power";
    }

    @Nullable
    private static SpellSchool school(MagicSchool school) {
        return school.isMagical() ? SpellSchools.getSchool(school.spellPowerId().toString()) : null;
    }

    @Nullable
    private static SpellPower.Result stats(SpellSchool school, LivingEntity caster) {
        try {
            return SpellPower.getSpellPower(school, caster);
        } catch (IllegalArgumentException missingAttribute) {
            return null;
        }
    }

    @Override
    public double basePower(LivingEntity caster, MagicSchool school) {
        double attack = VanillaMagicBackend.attackDamage(caster);
        SpellSchool spellSchool = school(school);
        SpellPower.Result stats = spellSchool == null ? null : stats(spellSchool, caster);
        return stats == null ? attack : attack + stats.baseValue() * RenegadesConfig.DAMAGE_MULTIPLIER.get();
    }

    @Override
    public double roll(LivingEntity caster, MagicSchool school, double base, @Nullable LivingEntity target) {
        SpellSchool spellSchool = school(school);
        SpellPower.Result stats = spellSchool == null ? null : stats(spellSchool, caster);
        if (stats == null) {
            return base;
        }
        SpellPower.Result result = new SpellPower.Result(spellSchool, base, stats.criticalChance(), stats.criticalDamage());
        return target != null ? result.randomValue(SpellPower.getVulnerability(target, spellSchool)) : result.randomValue();
    }

    @Override
    public DamageSource damageSource(LivingEntity caster, MagicSchool school, @Nullable Entity direct) {
        SpellSchool spellSchool = school(school);
        return spellSchool != null ? SpellDamageSource.mob(spellSchool, caster) : VanillaMagicBackend.INSTANCE.damageSource(caster, school, direct);
    }
}
