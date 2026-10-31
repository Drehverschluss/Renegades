// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.geometry;

public enum AttackShape {
    HORIZONTAL_ARC(AttackShape.Family.ARC),
    VERTICAL_ARC(AttackShape.Family.ARC),
    DIAGONAL_ARC(AttackShape.Family.ARC),
    THRUST_LANE(AttackShape.Family.LANE),
    FRONTAL_CONE(AttackShape.Family.ARC),
    CHARGE_PATH(AttackShape.Family.PATH),
    RADIAL_IMPACT(AttackShape.Family.RADIAL);

    private final AttackShape.Family family;

    AttackShape(AttackShape.Family family) {
        this.family = family;
    }

    public AttackShape.Family family() {
        return this.family;
    }

    public boolean isSwept() {
        return this == HORIZONTAL_ARC || this == VERTICAL_ARC || this == DIAGONAL_ARC || this == THRUST_LANE;
    }

    public boolean followsAttacker() {
        return this == CHARGE_PATH;
    }

    public enum Family {
        ARC,
        LANE,
        PATH,
        RADIAL;
    }
}
