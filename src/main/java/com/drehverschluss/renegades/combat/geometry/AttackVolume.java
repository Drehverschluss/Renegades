// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.geometry;

public record AttackVolume(
    AttackShape shape,
    SwingPath swing,
    double range,
    double innerRadius,
    double width,
    double verticalExtent,
    double offsetForward,
    double offsetLateral,
    double offsetVertical
) {
    public static AttackVolume sweep(SwingPath swing, double range, double verticalExtent) {
        return new AttackVolume(AttackShape.HORIZONTAL_ARC, swing, range, 0.7, 2.0, verticalExtent, 0.2, 0.35, 1.1);
    }

    public static AttackVolume cone(float spanDegrees, double range) {
        return new AttackVolume(AttackShape.FRONTAL_CONE, SwingPath.fixedSpan(spanDegrees), range, 0.0, 2.0, 2.4, 0.0, 0.0, 1.0);
    }

    public static AttackVolume path(double trailLength, double width, double verticalExtent) {
        return new AttackVolume(AttackShape.CHARGE_PATH, SwingPath.lane(0.0F), trailLength, 0.0, width, verticalExtent, 0.0, 0.0, 1.0);
    }

    public static AttackVolume radial(double range) {
        return new AttackVolume(AttackShape.RADIAL_IMPACT, SwingPath.fixedSpan(360.0F), range, 0.0, 2.0, 2.0, 0.0, 0.0, 0.4);
    }

    public AttackVolume withSwing(SwingPath swing) {
        return new AttackVolume(
            this.shape, swing, this.range, this.innerRadius, this.width, this.verticalExtent, this.offsetForward, this.offsetLateral, this.offsetVertical
        );
    }

    public AttackVolume withOrigin(double forward, double lateral, double vertical) {
        return new AttackVolume(this.shape, this.swing, this.range, this.innerRadius, this.width, this.verticalExtent, forward, lateral, vertical);
    }

    public AttackVolume withInnerRadius(double innerRadius) {
        return new AttackVolume(
            this.shape, this.swing, this.range, innerRadius, this.width, this.verticalExtent, this.offsetForward, this.offsetLateral, this.offsetVertical
        );
    }

    public float planeRoll() {
        return this.swing.planeRoll();
    }
}
