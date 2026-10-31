// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.geometry;

import net.minecraft.util.Mth;

public enum SwingDynamics {
    STEADY {
        @Override
        public float shape(float s) {
            return s;
        }
    },
    FORCEFUL {
        @Override
        public float shape(float s) {
            float once = s * s * (3.0F - 2.0F * s);
            return once * once * (3.0F - 2.0F * once);
        }
    },
    LUNGE {
        @Override
        public float shape(float s) {
            return s * s * (2.0F - s * 0.35F) / 1.65F;
        }
    };

    public abstract float shape(float var1);

    public final float apply(float s) {
        return this.shape(Mth.clamp(s, 0.0F, 1.0F));
    }
}
