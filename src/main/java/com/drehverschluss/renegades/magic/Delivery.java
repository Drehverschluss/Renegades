// Based on Delivery from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: area shapes are our AreaShape instead of AtlasVFX's TelegraphArea.
package com.drehverschluss.renegades.magic;

import javax.annotation.Nullable;

/**
 * How a spell reaches its targets: directly, as an area, a beam (cone), a projectile, a falling meteor or a
 * lingering cloud.
 */
public sealed interface Delivery permits Delivery.Direct, Delivery.Area, Delivery.Beam, Delivery.Projectile, Delivery.Meteor, Delivery.Cloud {
    enum Anchor {
        CASTER,
        TARGET
    }

    record Area(AreaShape shape, Anchor anchor, double height) implements Delivery {
        public static Area around(AreaShape shape) {
            return new Area(shape, Anchor.CASTER, 3.0);
        }

        public static Area at(AreaShape shape) {
            return new Area(shape, Anchor.TARGET, 3.0);
        }
    }

    record Beam(double range, float halfAngle) implements Delivery {
    }

    record Cloud(CloudSpec spec, Anchor anchor) implements Delivery {
    }

    record CloudSpec(AreaShape shape, double height, int durationTicks, int intervalTicks) {
    }

    record Direct() implements Delivery {
    }

    record Meteor(ProjectileSpec spec, double height) implements Delivery {
    }

    record Projectile(ProjectileSpec spec) implements Delivery {
    }

    /**
     * @param pierce number of extra targets the projectile flies through
     * @param burst  area hit around the impact point (also when hitting a block)
     */
    record ProjectileSpec(double speed, double gravity, float homingDegrees, int homingDelay, int homingEnd, int lifetimeTicks, int pierce,
                          @Nullable Area burst) {
        public static ProjectileSpec straight(double speed) {
            return new ProjectileSpec(speed, 0.0, 0.0F, 0, 0, 100, 0, null);
        }

        public ProjectileSpec withSpeed(double newSpeed) {
            return new ProjectileSpec(newSpeed, this.gravity, this.homingDegrees, this.homingDelay, this.homingEnd, this.lifetimeTicks, this.pierce, this.burst);
        }

        public ProjectileSpec homing(float degreesPerTick) {
            return this.homing(degreesPerTick, 0);
        }

        public ProjectileSpec homing(float degreesPerTick, int afterTicks) {
            return new ProjectileSpec(this.speed, this.gravity, degreesPerTick, afterTicks, this.homingEnd, this.lifetimeTicks, this.pierce, this.burst);
        }

        public ProjectileSpec homingUntil(int tick) {
            return new ProjectileSpec(this.speed, this.gravity, this.homingDegrees, this.homingDelay, tick, this.lifetimeTicks, this.pierce, this.burst);
        }

        public ProjectileSpec withGravity(double newGravity) {
            return new ProjectileSpec(this.speed, newGravity, this.homingDegrees, this.homingDelay, this.homingEnd, this.lifetimeTicks, this.pierce, this.burst);
        }

        public ProjectileSpec withLifetime(int ticks) {
            return new ProjectileSpec(this.speed, this.gravity, this.homingDegrees, this.homingDelay, this.homingEnd, ticks, this.pierce, this.burst);
        }

        public ProjectileSpec piercing(int extraTargets) {
            return new ProjectileSpec(this.speed, this.gravity, this.homingDegrees, this.homingDelay, this.homingEnd, this.lifetimeTicks, extraTargets, this.burst);
        }

        public ProjectileSpec bursting(AreaShape shape, double height) {
            return new ProjectileSpec(this.speed, this.gravity, this.homingDegrees, this.homingDelay, this.homingEnd, this.lifetimeTicks, this.pierce,
                    new Area(shape, Anchor.TARGET, height));
        }
    }
}
