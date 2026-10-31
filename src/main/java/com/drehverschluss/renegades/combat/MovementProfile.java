// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat;

public record MovementProfile(double maxSpeed, double accel, double arrest, double launchFloor, double snap, float turnDegrees, double lateralAuthority) {
    public static final MovementProfile HEAVY_ADVANCE = new MovementProfile(0.24, 0.13, 0.55, 0.02, 0.03, 7.0F, 0.3);
    public static final MovementProfile HEAVY_DESPERATE = new MovementProfile(0.29, 0.2, 0.62, 0.03, 0.03, 9.0F, 0.38);
    public static final MovementProfile RELENTLESS = new MovementProfile(0.27, 0.22, 0.62, 0.03, 0.028, 11.0F, 0.55);
    public static final MovementProfile CONDEMN = new MovementProfile(0.36, 0.28, 0.66, 0.04, 0.028, 12.0F, 0.45);
    public static final MovementProfile AGILE_ORBIT = new MovementProfile(0.25, 0.34, 0.7, 0.04, 0.025, 16.0F, 1.0);
    public static final MovementProfile AGILE_BURST = new MovementProfile(0.42, 0.45, 0.75, 0.06, 0.025, 13.0F, 0.85);
    public static final MovementProfile DISCIPLINED = new MovementProfile(0.18, 0.26, 0.72, 0.03, 0.03, 14.0F, 0.8);
    public static final MovementProfile TACTICAL_SPRINT = new MovementProfile(0.38, 0.3, 0.8, 0.05, 0.035, 10.0F, 0.4);
    public static final MovementProfile ELASTIC = new MovementProfile(0.22, 0.3, 0.68, 0.035, 0.026, 15.0F, 0.95);
    public static final MovementProfile CASTING_DRIFT = new MovementProfile(0.09, 0.22, 0.7, 0.02, 0.02, 9.0F, 1.0);
    public static final MovementProfile HARD_STOP = new MovementProfile(0.0, 0.5, 0.85, 0.05, 0.045, 10.0F, 0.0);

    public MovementProfile withMaxSpeed(double speed) {
        return new MovementProfile(speed, this.accel, this.arrest, this.launchFloor, this.snap, this.turnDegrees, this.lateralAuthority);
    }

    public MovementProfile urgent(double speedScale, double accelScale, float turnScale) {
        return new MovementProfile(
            this.maxSpeed * speedScale,
            Math.min(1.0, this.accel * accelScale),
            Math.min(1.0, this.arrest * accelScale),
            this.launchFloor * accelScale,
            this.snap,
            this.turnDegrees * turnScale,
            this.lateralAuthority
        );
    }
}
