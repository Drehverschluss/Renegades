// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.geometry;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class AttackAim {
    public static final float MAX_PITCH = 45.0F;

    private AttackAim() {
    }

    public static float rawPitchToward(LivingEntity attacker, Entity target) {
        double dx = target.getX() - attacker.getX();
        double dz = target.getZ() - attacker.getZ();
        double dy = target.getY() + target.getBbHeight() * 0.5 - (attacker.getY() + attacker.getBbHeight() * 0.5);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return horizontal < 1.0E-4 && Math.abs(dy) < 1.0E-4 ? 0.0F : (float)(-(Mth.atan2(dy, horizontal) * (180.0 / Math.PI)));
    }

    public static float pitchToward(LivingEntity attacker, Entity target) {
        return curve(rawPitchToward(attacker, target));
    }

    public static float curve(float rawDegrees) {
        float compressed = 45.0F * (float)Math.tanh(rawDegrees / 45.0F);
        return Mth.clamp(compressed, -45.0F, 45.0F);
    }

    public static boolean appliesTo(AttackVolume volume) {
        return volume.shape().family() != AttackShape.Family.RADIAL;
    }

    public static float ease(float current, float desired, float rate) {
        return current + (desired - current) * Mth.clamp(rate, 0.0F, 1.0F);
    }

    public interface Aiming {
        float attackPitch();
    }
}
