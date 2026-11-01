// Based on AbilityPresentation from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: vanilla particles (MagicParticles) and vanilla sounds (MagicSounds) instead of AtlasVFX cues and ANARCHY's
// own sound files; telegraphs are dust rings; optionally the look of another mod's spell (caster_visuals).
package com.drehverschluss.renegades.magic;

import com.drehverschluss.renegades.compat.spellengine.SpellEngineCompat;
import com.drehverschluss.renegades.visual.CasterVisuals;
import com.drehverschluss.renegades.visual.VisualRef;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Sounds and particles of spells. Each ability uses either its built-in look or, if caster_visuals assigns it a spell
 * (see CasterVisuals), that spell's particles and sounds; each part falls back on its own, so nothing plays twice.
 */
public final class AbilityPresentation {
    private static final float VOLUME = 1.5F;

    private AbilityPresentation() {
    }

    /**
     * Which parts of an effect the assigned spell's look took over; the caller plays the built-in look for the rest.
     */
    public record Handled(boolean particles, boolean sounds) {
        public static final Handled NONE = new Handled(false, false);
    }

    /**
     * The look the caster's ability gets right now (null = built-in look).
     */
    @Nullable
    public static VisualRef visual(LivingEntity caster, AbilitySpec spec) {
        return CasterVisuals.resolve(caster, spec.presentation().visual());
    }

    public static void windup(ServerLevel level, LivingEntity caster, AbilitySpec spec, CastTarget target) {
        AbilitySpec.Presentation presentation = spec.presentation();
        MagicSchool school = spec.lookSchool();
        Handled handled = spellWindup(level, caster, spec);
        if (!handled.sounds()) {
            sound(level, caster.position(), pick(presentation.windupSound(), MagicSounds.of(school).windup()));
        }
        if (!handled.particles()) {
            MagicParticles.pulse(level, school, caster.position().add(0.0, caster.getBbHeight() * 0.7, 0.0), 0.8F);
        }
        // The telegraph is a warning where the spell will land, not decoration: it stays with every look.
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
        Handled handled = spellRelease(level, caster, spec, target);
        if (!handled.sounds()) {
            sound(level, caster.position(), pick(presentation.releaseSound(), MagicSounds.of(school).release()));
        }
        if (!handled.particles() && (spec.delivery() instanceof Delivery.Projectile || spec.delivery() instanceof Delivery.Beam)) {
            MagicParticles.pulse(level, school, caster.getEyePosition(), 0.8F);
        }
    }

    /**
     * Effects where an area spell lands: a nova around the caster, an explosion elsewhere. With a spell look, each
     * target struck shows the spell's impact; the area itself shows the spell's release particles (see release), or
     * the built-in area particles if the spell has none (e.g. a cone spell lending its look to a nova).
     */
    static void areaHit(ServerLevel level, LivingEntity caster, AbilitySpec spec, Delivery.Area area, Vec3 origin, List<LivingEntity> struck) {
        VisualRef ref = visual(caster, spec);
        if (ref != null) {
            for (LivingEntity entity : struck) {
                SpellEngineCompat.impact(level, caster, ref.spell(), entity, entity.getBoundingBox().getCenter(), ref.particles(), ref.sounds());
            }
            if (ref.particles() && (area.anchor() != Delivery.Anchor.CASTER || SpellEngineCompat.hasReleaseParticles(level, ref.spell()))) {
                return;
            }
        }
        if (area.anchor() == Delivery.Anchor.CASTER) {
            MagicParticles.nova(level, spec.lookSchool(), origin, (float) area.shape().extent());
        } else {
            MagicParticles.explosion(level, spec.lookSchool(), origin, (float) area.shape().extent());
        }
    }

    /**
     * Impact of a spell on a target (or at a point, e.g. a projectile hitting a block); radius > 0 for a burst.
     */
    public static void impact(ServerLevel level, LivingEntity caster, AbilitySpec spec, @Nullable VisualRef ref, @Nullable Entity target, Vec3 at,
                              float radius) {
        AbilitySpec.Presentation presentation = spec.presentation();
        MagicSchool school = spec.lookSchool();
        Handled handled = Handled.NONE;
        if (ref != null) {
            SpellEngineCompat.impact(level, caster, ref.spell(), target, at, ref.particles(), ref.sounds());
            handled = new Handled(ref.particles(), ref.sounds());
        }
        if (!handled.sounds()) {
            sound(level, at, pick(presentation.impactSound(), radius > 0.0F ? MagicSounds.EXPLOSION : MagicSounds.of(school).impact()));
        }
        if (!handled.particles()) {
            if (radius > 0.0F) {
                MagicParticles.explosion(level, school, at, radius);
            } else {
                MagicParticles.pulse(level, school, at, 1.0F);
            }
        }
    }

    /**
     * One pulse of a lingering cloud: with a spell look, the spell's release particles fill the cloud's area and each
     * target shows the spell's impact.
     */
    public static void cloudPulse(ServerLevel level, LivingEntity caster, AbilitySpec spec, @Nullable VisualRef ref, Vec3 at, float radius,
                                  List<LivingEntity> struck) {
        if (ref != null) {
            SpellEngineCompat.release(level, caster, ref.spell(), at, radius, ref.particles(), false);
            for (LivingEntity entity : struck) {
                SpellEngineCompat.impact(level, caster, ref.spell(), entity, entity.getBoundingBox().getCenter(), ref.particles(), ref.sounds());
            }
            if (ref.particles() && SpellEngineCompat.hasReleaseParticles(level, ref.spell())) {
                return;
            }
        }
        MagicParticles.wisp(level, spec.lookSchool(), at, radius);
    }

    /**
     * The assigned spell's casting particles and sounds, if the ability has a spell look.
     */
    public static Handled spellWindup(ServerLevel level, LivingEntity caster, AbilitySpec spec) {
        VisualRef ref = visual(caster, spec);
        if (ref == null) {
            return Handled.NONE;
        }
        SpellEngineCompat.cast(level, caster, ref.spell(), ref.particles(), ref.sounds());
        return new Handled(ref.particles(), ref.sounds());
    }

    /**
     * The assigned spell's release particles and sounds, if the ability has a spell look. Area particles appear where
     * the area is; a cloud shows them with each pulse instead.
     */
    public static Handled spellRelease(ServerLevel level, LivingEntity caster, AbilitySpec spec, CastTarget target) {
        VisualRef ref = visual(caster, spec);
        if (ref == null) {
            return Handled.NONE;
        }
        Vec3 at = null;
        float radius = 0.0F;
        boolean particles = ref.particles();
        if (spec.delivery() instanceof Delivery.Area area) {
            radius = (float) area.shape().extent();
            at = area.anchor() == Delivery.Anchor.CASTER ? null : target.position();
        } else if (spec.delivery() instanceof Delivery.Cloud) {
            particles = false;
        }
        SpellEngineCompat.release(level, caster, ref.spell(), at, radius, particles, ref.sounds());
        return new Handled(ref.particles(), ref.sounds());
    }

    /**
     * The assigned spell's impact particles and sounds on a target, if the ability has a spell look (for abilities
     * that apply their effect directly, e.g. heals).
     */
    public static Handled spellImpact(ServerLevel level, LivingEntity caster, AbilitySpec spec, LivingEntity target) {
        VisualRef ref = visual(caster, spec);
        if (ref == null) {
            return Handled.NONE;
        }
        SpellEngineCompat.impact(level, caster, ref.spell(), target, target.getBoundingBox().getCenter(), ref.particles(), ref.sounds());
        return new Handled(ref.particles(), ref.sounds());
    }

    private static SoundEvent pick(@Nullable SoundEvent override, SoundEvent fallback) {
        return override != null ? override : fallback;
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound) {
        MagicParticles.sound(level, at, sound, VOLUME, 0.9F + level.random.nextFloat() * 0.2F);
    }
}
