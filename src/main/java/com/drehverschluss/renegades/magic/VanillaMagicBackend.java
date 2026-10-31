// Based on VanillaMagicBackend from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.magic;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Backend without Spell Power: the power of every school is the caster's attack damage attribute (which already
 * includes the strength multiplier, the held weapon and mob level bonuses). Magic damage uses the school's damage
 * type renegades:&lt;school&gt;.
 */
public final class VanillaMagicBackend implements MagicBackend {
    public static final VanillaMagicBackend INSTANCE = new VanillaMagicBackend();

    private VanillaMagicBackend() {
    }

    @Override
    public String name() {
        return "vanilla";
    }

    @Override
    public double basePower(LivingEntity caster, MagicSchool school) {
        return attackDamage(caster);
    }

    @Override
    public double roll(LivingEntity caster, MagicSchool school, double base, @Nullable LivingEntity target) {
        return base;
    }

    @Override
    public DamageSource damageSource(LivingEntity caster, MagicSchool school, @Nullable Entity direct) {
        DamageSources sources = caster.damageSources();
        if (!school.isMagical()) {
            return direct != null ? sources.mobProjectile(direct, caster) : sources.mobAttack(caster);
        }
        Holder<DamageType> type = caster.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(school.damageType());
        return new DamageSource(type, direct != null ? direct : caster, caster);
    }

    public static double attackDamage(LivingEntity caster) {
        AttributeInstance attribute = caster.getAttribute(Attributes.ATTACK_DAMAGE);
        return attribute == null ? 0.0 : attribute.getValue();
    }
}
