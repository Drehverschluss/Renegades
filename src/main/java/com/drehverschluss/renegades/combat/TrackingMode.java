// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat;

public enum TrackingMode {
    FULL(20.0F),
    REDUCED(4.5F),
    MINIMAL(1.2F),
    LOCKED(0.0F);

    private final float degreesPerTick;

    TrackingMode(float degreesPerTick) {
        this.degreesPerTick = degreesPerTick;
    }

    public float degreesPerTick() {
        return this.degreesPerTick;
    }
}
