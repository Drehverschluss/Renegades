// Based on Impact from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.magic;

import com.drehverschluss.renegades.config.RenegadesConfig;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;

/**
 * What a spell does to each target it reaches.
 */
public sealed interface Impact permits Impact.Damage, Impact.Ignite, Impact.ApplyEffect, Impact.ApplyToCaster, Impact.Knockback, Impact.Heal, Impact.Launch,
        Impact.TimedAttribute {
    void apply(ImpactContext context, LivingEntity target);

    /**
     * Status effect on the target (e.g. slowness of frost spells).
     */
    record ApplyEffect(Holder<MobEffect> effect, int durationTicks, int amplifier) implements Impact {
        @Override
        public void apply(ImpactContext context, LivingEntity target) {
            target.addEffect(new MobEffectInstance(this.effect, this.durationTicks, this.amplifier), context.caster());
        }
    }

    /**
     * Status effect on the caster (e.g. a buff when the spell lands).
     */
    record ApplyToCaster(Holder<MobEffect> effect, int durationTicks, int amplifier) implements Impact {
        @Override
        public void apply(ImpactContext context, LivingEntity target) {
            context.caster().addEffect(new MobEffectInstance(this.effect, this.durationTicks, this.amplifier, false, false));
        }
    }

    /**
     * Spell damage: the caster's power for the school (see MagicBackend) times the coefficient.
     *
     * @param bypassIframes hits even right after another hit (rapid bolts and bursts)
     * @param school        overrides the spell's school for the damage type (null = the spell's school)
     */
    record Damage(float coefficient, float knockback, boolean bypassIframes, @Nullable MagicSchool school) implements Impact {
        public Damage(float coefficient, float knockback, boolean bypassIframes) {
            this(coefficient, knockback, bypassIframes, null);
        }

        public static Damage of(float coefficient) {
            return new Damage(coefficient, 1.0F, true);
        }

        public static Damage as(MagicSchool school, float coefficient, float knockback) {
            return new Damage(coefficient, knockback, true, school);
        }

        @Override
        public void apply(ImpactContext context, LivingEntity target) {
            MagicBackend backend = MagicBackends.active();
            double amount = backend.roll(context.caster(), context.school(), context.basePower(), target) * this.coefficient * context.scale();
            if (amount <= 0.0) {
                return;
            }
            if (this.bypassIframes) {
                target.invulnerableTime = 0;
            }
            MagicSchool dealt = this.school != null ? this.school : context.school();
            DamageSource source = backend.damageSource(context.caster(), dealt, context.direct());
            if (target.hurt(source, (float) amount) && this.knockback > 0.0F) {
                Knockback.push(context, target, 0.4 * this.knockback, 0.0);
            }
        }

        /**
         * The damage without critical strikes or the target's resistances (e.g. how hard a blocked bolt hits the shield).
         */
        public double expected(ImpactContext context) {
            return context.basePower() * this.coefficient * context.scale();
        }
    }

    /**
     * Healing by the caster's power for the school times the coefficient and the healingMultiplier config (for
     * supporting classes), never above maxFraction of the target's max health per application (0 = no cap) and never
     * above max health. A caster that is a HealingCaster is told how much was actually healed.
     */
    record Heal(float coefficient, float maxFraction) implements Impact {
        public Heal(float coefficient) {
            this(coefficient, 0.0F);
        }

        @Override
        public void apply(ImpactContext context, LivingEntity target) {
            double amount = MagicBackends.active().roll(context.caster(), context.school(), context.basePower(), null) * this.coefficient * context.scale()
                    * RenegadesConfig.HEALING_MULTIPLIER.get();
            if (this.maxFraction > 0.0F) {
                amount = Math.min(amount, target.getMaxHealth() * this.maxFraction);
            }
            if (amount <= 0.0 || target.getHealth() >= target.getMaxHealth()) {
                return;
            }
            float before = target.getHealth();
            target.heal((float) amount);
            float healed = target.getHealth() - before;
            if (healed > 0.0F && context.caster() instanceof HealingCaster healer) {
                healer.onHealed(target, healed);
            }
        }
    }

    /**
     * Sets the target (never blocks) on fire.
     */
    record Ignite(float seconds) implements Impact {
        @Override
        public void apply(ImpactContext context, LivingEntity target) {
            target.igniteForSeconds(this.seconds);
        }
    }

    /**
     * Throws the target straight up (vertical speed in blocks per tick, horizontal speed mostly taken away) and gives it
     * Slow Falling for the way down, so the throw itself causes no fall damage.
     */
    record Launch(double vertical, int slowFallingTicks) implements Impact {
        @Override
        public void apply(ImpactContext context, LivingEntity target) {
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(motion.x * 0.2, this.vertical, motion.z * 0.2);
            target.setOnGround(false);
            target.hasImpulse = true;
            target.hurtMarked = true;
            target.resetFallDistance();
            target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, this.slowFallingTicks, 0), context.caster());
        }
    }

    /**
     * An attribute modifier on the target for some ticks (see TimedModifiers), e.g. another mod's "damage taken"
     * attribute; nothing if the target does not have that attribute.
     */
    record TimedAttribute(ResourceLocation attribute, ResourceLocation modifierId, double amount, AttributeModifier.Operation operation, int ticks)
            implements Impact {
        @Override
        public void apply(ImpactContext context, LivingEntity target) {
            if (target.level() instanceof ServerLevel level) {
                TimedModifiers.apply(level, target, this.attribute, this.modifierId, this.amount, this.operation, this.ticks);
            }
        }
    }

    record Knockback(double horizontal, double vertical) implements Impact {
        @Override
        public void apply(ImpactContext context, LivingEntity target) {
            push(context, target, this.horizontal, this.vertical);
        }

        static void push(ImpactContext context, LivingEntity target, double horizontal, double vertical) {
            Vec3 away = target.position().subtract(context.origin());
            double dx = away.x;
            double dz = away.z;
            if (dx * dx + dz * dz < 1.0E-6) {
                Vec3 facing = context.caster().getLookAngle();
                dx = facing.x;
                dz = facing.z;
            }
            target.knockback(horizontal, -dx, -dz);
            if (vertical != 0.0) {
                target.push(0.0, vertical, 0.0);
                target.hurtMarked = true;
            }
        }
    }
}
