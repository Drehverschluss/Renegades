// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat;

import net.minecraft.core.particles.ParticleOptions;

public record SlashVisual(ParticleOptions particle, int perTick, double speed, double scatter) {
    public static SlashVisual heavy(ParticleOptions particle) {
        return new SlashVisual(particle, 26, 0.24, 0.08);
    }

    public static SlashVisual light(ParticleOptions particle) {
        return new SlashVisual(particle, 16, 0.32, 0.05);
    }

    public static SlashVisual blunt(ParticleOptions particle) {
        return new SlashVisual(particle, 10, 0.16, 0.12);
    }

    public SlashVisual withCount(int perTick) {
        return new SlashVisual(this.particle, perTick, this.speed, this.scatter);
    }
}
