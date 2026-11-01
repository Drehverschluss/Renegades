package com.drehverschluss.renegades.compat.spellengine;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.Fx;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.fx.ParticleHelper;

/**
 * Server side of the Spell Engine look: plays the particles and sounds of a spell's data for our own spells. Only uses
 * Spell Engine's public data and its particle packets (ParticleHelper); no spell is cast, nothing deals damage. Only
 * loaded when Spell Engine is installed (see SpellEngineCompat).
 */
final class SpellEngineVisuals {
    private SpellEngineVisuals() {
    }

    @Nullable
    static Spell spell(Level level, ResourceLocation id) {
        return SpellRegistry.from(level).getHolder(id).map(holder -> holder.value()).orElse(null);
    }

    static boolean exists(Level level, ResourceLocation id) {
        return spell(level, id) != null;
    }

    static void cast(ServerLevel level, LivingEntity caster, ResourceLocation id, boolean particles, boolean sounds) {
        Spell spell = spell(level, id);
        if (spell == null || spell.active == null) {
            return;
        }
        Spell.Active.Cast cast = spell.active.cast;
        if (particles) {
            send(caster, cast.particles);
        }
        if (sounds) {
            play(level, caster.position(), cast.start_sound);
            play(level, caster.position(), cast.sound);
        }
    }

    static void release(ServerLevel level, LivingEntity caster, ResourceLocation id, @Nullable Vec3 at, float areaRadius, boolean particles,
                        boolean sounds) {
        Spell spell = spell(level, id);
        if (spell == null) {
            return;
        }
        Fx.Visuals visuals = spell.release.visuals;
        if (particles && visuals != null && !visuals.particles.isEmpty()) {
            // Like Spell Engine's release effects: particles that scale with the range get our area's radius.
            List<ParticleGroup> scaled = (areaRadius > 0.0F ? visuals.resolved(Fx.Context.ofRange(areaRadius)) : visuals).particles;
            if (areaRadius > 0.0F && spell.range > 0.0F) {
                scaled = fitSpread(scaled, areaRadius / spell.range);
            }
            if (at == null) {
                ParticleHelper.sendBatches(caster, scaled);
            } else {
                ParticleHelper.sendBatches(at, caster, scaled);
            }
        }
        if (sounds) {
            Vec3 soundAt = at != null ? at : caster.position();
            play(level, soundAt, spell.release.sound);
            if (spell.deliver != null && spell.deliver.type == Spell.Delivery.Type.PROJECTILE && spell.deliver.projectile != null) {
                play(level, caster.position(), spell.deliver.projectile.launch_properties.sound);
            }
        }
    }

    /**
     * Pillar and ring particles spread by a fixed extent that fits the spell's own range; scaled to our area so the
     * look does not promise a bigger area than the one that is actually hit.
     */
    private static List<ParticleGroup> fitSpread(List<ParticleGroup> groups, float factor) {
        if (Math.abs(factor - 1.0F) < 0.01F) {
            return groups;
        }
        return groups.stream().map(group -> {
            if (group.batch == null || group.batch.extent <= 0.0F
                    || (group.batch.shape != ParticleGroup.Shape.PILLAR && group.batch.shape != ParticleGroup.Shape.PIPE)) {
                return group;
            }
            ParticleGroup copy = group.copy();
            copy.batch.extent = group.batch.extent * factor;
            return copy;
        }).toList();
    }

    static boolean hasReleaseParticles(Level level, ResourceLocation id) {
        Spell spell = spell(level, id);
        return spell != null && spell.release.visuals != null && !spell.release.visuals.particles.isEmpty();
    }

    static void impact(ServerLevel level, LivingEntity caster, ResourceLocation id, @Nullable Entity target, Vec3 at, boolean particles, boolean sounds) {
        Spell spell = spell(level, id);
        if (spell == null) {
            return;
        }
        Vec3 soundAt = target != null ? target.position() : at;
        for (Spell.Impact impact : spell.impacts) {
            if (particles && impact.visuals != null && !impact.visuals.particles.isEmpty()) {
                if (target != null) {
                    ParticleHelper.sendBatches(target, impact.visuals.particles);
                } else {
                    ParticleHelper.sendBatches(at, caster, impact.visuals.particles);
                }
            }
            if (sounds) {
                play(level, soundAt, impact.sound);
            }
        }
    }

    static int projectileLook(Level level, ResourceLocation id) {
        Spell.ProjectileData data = projectileData(spell(level, id));
        if (data == null) {
            return 0;
        }
        int flags = 0;
        if (data.client_data != null && data.client_data.composite_model != null && !data.client_data.composite_model.models.isEmpty()) {
            flags |= SpellEngineCompat.PROJECTILE_MODEL;
        }
        if (data.client_data != null && !data.client_data.travel_particles.isEmpty()) {
            flags |= SpellEngineCompat.PROJECTILE_TRAIL;
        }
        if (data.travel_sound != null) {
            flags |= SpellEngineCompat.PROJECTILE_TRAVEL_SOUND;
        }
        return flags;
    }

    /**
     * Like SpellProjectile: the travel sound every travel_sound_interval ticks.
     */
    static void travelSound(ServerLevel level, Entity projectile, ResourceLocation id) {
        Spell.ProjectileData data = projectileData(spell(level, id));
        if (data != null && data.travel_sound != null && projectile.tickCount % Math.max(1, data.travel_sound_interval) == 0) {
            play(level, projectile.position(), data.travel_sound);
        }
    }

    /**
     * Client side, every tick, like SpellProjectile#tick: the travel particles around the projectile.
     */
    static void travelParticles(Entity projectile, ResourceLocation id) {
        Spell.ProjectileData data = projectileData(spell(projectile.level(), id));
        if (data != null && data.client_data != null) {
            for (ParticleGroup group : data.client_data.travel_particles) {
                ParticleHelper.play(projectile.level(), projectile, projectile.getYRot(), projectile.getXRot(), group);
            }
        }
    }

    /**
     * Same choice as SpellProjectile#projectileData: projectile and meteor deliveries carry projectile data.
     */
    @Nullable
    static Spell.ProjectileData projectileData(@Nullable Spell spell) {
        if (spell == null || spell.deliver == null) {
            return null;
        }
        return switch (spell.deliver.type) {
            case PROJECTILE -> spell.deliver.projectile == null ? null : spell.deliver.projectile.projectile;
            case METEOR -> spell.deliver.meteor == null ? null : spell.deliver.meteor.projectile;
            default -> null;
        };
    }

    private static void send(Entity anchor, @Nullable List<ParticleGroup> particles) {
        if (particles != null && !particles.isEmpty()) {
            ParticleHelper.sendBatches(anchor, particles);
        }
    }

    /**
     * Like Spell Engine's SoundHelper (volume, randomized pitch), but at a position and as a hostile sound.
     */
    private static void play(ServerLevel level, Vec3 at, @Nullable Sound sound) {
        if (sound == null || sound.id() == null) {
            return;
        }
        ResourceLocation soundId = ResourceLocation.tryParse(sound.id());
        SoundEvent event = soundId == null ? null : BuiltInRegistries.SOUND_EVENT.get(soundId);
        if (event != null) {
            level.playSound(null, at.x, at.y, at.z, event, SoundSource.HOSTILE, sound.volume(), sound.randomizedPitch());
        }
    }
}
