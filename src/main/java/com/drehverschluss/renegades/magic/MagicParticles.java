package com.drehverschluss.renegades.magic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla particle effects for spells (replaces ANARCHY's AtlasVFX cues: pulse, nova, explosion, telegraph, wisp).
 * Everything is sent from the server, so all players nearby see it.
 */
public final class MagicParticles {
    private MagicParticles() {
    }

    /**
     * Small flash at a point (casting hands, projectile hits).
     */
    public static void pulse(ServerLevel level, MagicSchool school, Vec3 at, float size) {
        level.sendParticles(school.particle(), at.x, at.y, at.z, Math.max(4, (int) (8 * size)), 0.15 * size, 0.15 * size, 0.15 * size, 0.02);
        level.sendParticles(school.dust(1.2F), at.x, at.y, at.z, Math.max(3, (int) (6 * size)), 0.2 * size, 0.2 * size, 0.2 * size, 0.0);
    }

    /**
     * Burst filling a sphere of the given radius (projectile bursts, area hits).
     */
    public static void explosion(ServerLevel level, MagicSchool school, Vec3 at, float radius) {
        int count = Mth.clamp((int) (radius * radius * 6), 12, 120);
        level.sendParticles(school.particle(), at.x, at.y + 0.3, at.z, count, radius * 0.4, 0.4, radius * 0.4, 0.06);
        level.sendParticles(school.dust(1.6F), at.x, at.y + 0.3, at.z, count / 2, radius * 0.4, 0.3, radius * 0.4, 0.0);
        if (radius >= 2.0F) {
            ring(level, school, at, radius);
        }
    }

    /**
     * Expanding rings on the ground around the caster (novas).
     */
    public static void nova(ServerLevel level, MagicSchool school, Vec3 at, float radius) {
        for (float r = 1.0F; r <= radius; r += 1.25F) {
            ring(level, school, at, r);
        }
        level.sendParticles(school.particle(), at.x, at.y + 0.5, at.z, 30, radius * 0.35, 0.3, radius * 0.35, 0.08);
    }

    /**
     * Ring of dust marking an area on the ground (telegraphs and edges).
     */
    public static void ring(ServerLevel level, MagicSchool school, Vec3 at, float radius) {
        int points = Mth.clamp((int) (radius * 10), 12, 80);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            level.sendParticles(school.dust(1.0F), at.x + Math.cos(angle) * radius, at.y + 0.1, at.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Drifting particles inside an area (lingering clouds).
     */
    public static void wisp(ServerLevel level, MagicSchool school, Vec3 at, float radius) {
        level.sendParticles(school.particle(), at.x, at.y + 0.4, at.z, Math.max(6, (int) (radius * 4)), radius * 0.5, 0.3, radius * 0.5, 0.01);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }
}
