// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.geometry;

public enum Handedness {
    RIGHT(1.0),
    LEFT(-1.0),
    CENTRED(0.0);

    private final double lateralSign;

    Handedness(double lateralSign) {
        this.lateralSign = lateralSign;
    }

    public double lateralSign() {
        return this.lateralSign;
    }

    public Handedness opposite() {
        return switch (this) {
            case RIGHT -> LEFT;
            case LEFT -> RIGHT;
            case CENTRED -> CENTRED;
        };
    }
}
