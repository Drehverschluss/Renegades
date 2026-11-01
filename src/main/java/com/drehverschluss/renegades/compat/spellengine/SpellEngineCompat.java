package com.drehverschluss.renegades.compat.spellengine;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Entry point for the optional Spell Engine integration: only the look of spells (models, particles, sounds) for our
 * casters. Must not reference any Spell Engine class, so the mod runs without it; Spell Engine is only touched in
 * SpellEngineVisuals and SpellEngineVisualsClient, which are only loaded when it is installed.
 */
public final class SpellEngineCompat {
    public static final String MOD_ID = "spell_engine";
    // Projectile look flags (synced on our projectiles): model, travel particles, travel sound.
    public static final int PROJECTILE_MODEL = 1;
    public static final int PROJECTILE_TRAIL = 2;
    public static final int PROJECTILE_TRAVEL_SOUND = 4;

    private SpellEngineCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean spellExists(Level level, ResourceLocation spell) {
        return isLoaded() && SpellEngineVisuals.exists(level, spell);
    }

    /**
     * Casting particles and sounds (at the start of the windup).
     */
    public static void cast(ServerLevel level, LivingEntity caster, ResourceLocation spell, boolean particles, boolean sounds) {
        SpellEngineVisuals.cast(level, caster, spell, particles, sounds);
    }

    /**
     * Release particles (scaled to the area radius, 0 = as authored; at a position, or around the caster if null) and
     * the release and launch sounds.
     */
    public static void release(ServerLevel level, LivingEntity caster, ResourceLocation spell, @Nullable Vec3 at, float areaRadius, boolean particles,
                               boolean sounds) {
        SpellEngineVisuals.release(level, caster, spell, at, areaRadius, particles, sounds);
    }

    /**
     * Whether the spell has particles of its own when it is released (e.g. a ring for an area spell).
     */
    public static boolean hasReleaseParticles(Level level, ResourceLocation spell) {
        return SpellEngineVisuals.hasReleaseParticles(level, spell);
    }

    /**
     * The spell's impact particles and sounds on a target (or at a position if there is none).
     */
    public static void impact(ServerLevel level, LivingEntity caster, ResourceLocation spell, @Nullable Entity target, Vec3 at, boolean particles,
                              boolean sounds) {
        SpellEngineVisuals.impact(level, caster, spell, target, at, particles, sounds);
    }

    /**
     * Which parts of the spell's projectile look exist (PROJECTILE_* flags).
     */
    public static int projectileLook(Level level, ResourceLocation spell) {
        return SpellEngineVisuals.projectileLook(level, spell);
    }

    public static void travelSound(ServerLevel level, Entity projectile, ResourceLocation spell) {
        SpellEngineVisuals.travelSound(level, projectile, spell);
    }

    /**
     * Client side: the spell's travel particles around a projectile (call every tick).
     */
    public static void travelParticles(Entity projectile, ResourceLocation spell) {
        SpellEngineVisuals.travelParticles(projectile, spell);
    }
}
