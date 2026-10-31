// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat;

public enum AttackMotion {
    PLANTED(true, true),
    PRESSING(true, false),
    MOBILE(false, false),
    COMMITTED_VECTOR(true, true),
    CROSS_THROUGH(true, true),
    RETREATING(true, true);

    private final boolean ownsMovement;
    private final boolean arrestsOnWindup;

    AttackMotion(boolean ownsMovement, boolean arrestsOnWindup) {
        this.ownsMovement = ownsMovement;
        this.arrestsOnWindup = arrestsOnWindup;
    }

    public boolean ownsMovement() {
        return this.ownsMovement;
    }

    public boolean arrestsOnWindup() {
        return this.arrestsOnWindup;
    }

    public boolean isCommitted() {
        return this == COMMITTED_VECTOR || this == CROSS_THROUGH || this == RETREATING;
    }
}
