// Based on AbilityPresentation from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: vanilla particles (MagicParticles) and vanilla sounds (MagicSounds) instead of AtlasVFX cues and ANARCHY's
// own sound files; telegraphs are dust rings.
package com.drehverschluss.renegades.magic;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Sounds and particles of spells.
 */
public final class AbilityPresentation {
    private static final float VOLUME = 1.5F;

    private AbilityPresentation() {
    }

    public static void windup(ServerLevel level, LivingEntity caster, AbilitySpec spec, CastTarget target) {
        AbilitySpec.Presentation presentation = spec.presentation();
        MagicSchool school = spec.lookSchool();
        sound(level, caster.position(), pick(presentation.windupSound(), MagicSounds.of(school).windup()));
        MagicParticles.pulse(level, school, caster.position().add(0.0, caster.getBbHeight() * 0.7, 0.0), 0.8F);
        if (presentation.telegraph()) {
            if (spec.delivery() instanceof Delivery.Area area) {
                MagicParticles.ring(level, school, AbilityExecutor.anchor(caster, area.anchor(), target), (float) area.shape().extent());
            } else if (spec.delivery() instanceof Delivery.Cloud cloud) {
                MagicParticles.ring(level, school, AbilityExecutor.anchor(caster, cloud.anchor(), target), (float) cloud.spec().shape().extent());
            }
        }
    }

    public static void release(ServerLevel level, LivingEntity caster, AbilitySpec spec, CastTarget target) {
        AbilitySpec.Presentation presentation = spec.presentation();
        MagicSchool school = spec.lookSchool();
        sound(level, caster.position(), pick(presentation.releaseSound(), MagicSounds.of(school).release()));
        if (spec.delivery() instanceof Delivery.Projectile || spec.delivery() instanceof Delivery.Beam) {
            MagicParticles.pulse(level, school, caster.getEyePosition(), 0.8F);
        }
    }

    /**
     * Effects where an area spell lands: a nova around the caster, an explosion elsewhere.
     */
    static void areaHit(ServerLevel level, AbilitySpec spec, Delivery.Area area, Vec3 origin) {
        if (area.anchor() == Delivery.Anchor.CASTER) {
            MagicParticles.nova(level, spec.lookSchool(), origin, (float) area.shape().extent());
        } else {
            MagicParticles.explosion(level, spec.lookSchool(), origin, (float) area.shape().extent());
        }
    }

    public static void impact(ServerLevel level, AbilitySpec spec, Vec3 at, float radius) {
        AbilitySpec.Presentation presentation = spec.presentation();
        MagicSchool school = spec.lookSchool();
        sound(level, at, pick(presentation.impactSound(), radius > 0.0F ? MagicSounds.EXPLOSION : MagicSounds.of(school).impact()));
        if (radius > 0.0F) {
            MagicParticles.explosion(level, school, at, radius);
        } else {
            MagicParticles.pulse(level, school, at, 1.0F);
        }
    }

    public static void cloudPulse(ServerLevel level, AbilitySpec spec, Vec3 at, float radius) {
        MagicParticles.wisp(level, spec.lookSchool(), at, radius);
    }

    private static SoundEvent pick(@Nullable SoundEvent override, SoundEvent fallback) {
        return override != null ? override : fallback;
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound) {
        MagicParticles.sound(level, at, sound, VOLUME, 0.9F + level.random.nextFloat() * 0.2F);
    }
}
