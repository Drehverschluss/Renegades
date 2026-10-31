// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.geometry;

public enum AttackPlane {
    HORIZONTAL(0.0F),
    DIAGONAL_SHALLOW(35.0F),
    DIAGONAL_STEEP(68.0F),
    VERTICAL(90.0F),
    CUSTOM(0.0F);

    private final float defaultRoll;

    AttackPlane(float defaultRoll) {
        this.defaultRoll = defaultRoll;
    }

    public float defaultRoll() {
        return this.defaultRoll;
    }
}
