// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: limitApproach keeps the renegade from walking or dashing through its target (entities do not block each
// other); all combat movement is scaled by the movement speed attribute (speedFactor).
package com.drehverschluss.renegades.combat;

import com.drehverschluss.renegades.entity.RenegadeEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class GroundLocomotion {
    private static final double REST_EPSILON = 1.0E-4;
    private static final int STALL_TICKS = 6;
    private static final double STALL_DISTANCE_SQ = 0.0016;
    private static final int FALLBACK_TICKS = 30;
    // Minimum gap between the hitboxes of the renegade and its target that steering and attack motion may close.
    private static final double MIN_TARGET_GAP = 0.5;
    private static final double MAX_SPEED_FACTOR = 2.0;
    private double speedFactor = 1.0;
    private final RenegadeEntity entity;
    private MovementOwner owner = MovementOwner.NAVIGATION;
    private Vec3 steering = Vec3.ZERO;
    private Vec3 attackMotion = Vec3.ZERO;
    private boolean intentThisTick;
    private MovementProfile lastProfile = MovementProfile.HARD_STOP;
    private Vec3 committedVelocity = Vec3.ZERO;
    private int committedTicks;
    private boolean committedAirborne;
    private int committedExtension;
    private double committedCorrection;
    @Nullable
    private Vec3 committedAim;
    private Vec3 previousPosition = Vec3.ZERO;
    private int stalledTicks;
    private int fallbackTicks;
    @Nullable
    private Vec3 fallbackGoal;
    private double fallbackSpeed = 1.0;
    private int impulseTicks;

    public GroundLocomotion(RenegadeEntity entity) {
        this.entity = entity;
        this.previousPosition = entity.position();
    }

    public void beginTick() {
        this.attackMotion = Vec3.ZERO;
        this.intentThisTick = false;
        this.speedFactor = this.computeSpeedFactor();
        if (this.impulseTicks > 0) {
            this.impulseTicks--;
        }

        if (this.fallbackTicks > 0) {
            this.fallbackTicks--;
        }

        this.trackProgress();
    }

    /**
     * Current movement speed attribute relative to the entity type's default, clamped to 0..MAX_SPEED_FACTOR. Scales
     * all combat steering, attack advances and dashes, so Slowness, slowing spells and level bonuses apply in combat.
     */
    private double computeSpeedFactor() {
        AttributeInstance speed = this.entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return 1.0;
        }
        @SuppressWarnings("unchecked")
        AttributeSupplier defaults = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) this.entity.getType());
        double base = defaults.hasAttribute(Attributes.MOVEMENT_SPEED) ? defaults.getBaseValue(Attributes.MOVEMENT_SPEED) : speed.getBaseValue();
        return base <= 0.0 ? 1.0 : Mth.clamp(speed.getValue() / base, 0.0, MAX_SPEED_FACTOR);
    }

    public double speedFactor() {
        return this.speedFactor;
    }

    public void endTick() {
        if (this.committedTicks > 0) {
            this.tickCommitted();
        } else if (this.owner == MovementOwner.NAVIGATION) {
            this.steering = horizontal(this.entity.getDeltaMovement());
        } else {
            if (!this.intentThisTick) {
                this.driveTo(Vec3.ZERO, MovementProfile.HARD_STOP);
            }

            this.writeVelocity();
        }
    }

    public void pressure(LivingEntity target, double preferredRange, MovementProfile profile) {
        Vec3 toTarget = horizontal(target.position().subtract(this.entity.position()));
        double distance = toTarget.length();
        Vec3 forward = distance < 1.0E-4 ? this.facing() : toTarget.normalize();
        Vec3 lateral = Vec3.ZERO;
        double contact = Math.max(1.2, preferredRange * 0.45);
        double speed;
        if (distance > preferredRange * 2.0) {
            speed = profile.maxSpeed();
        } else if (distance > preferredRange) {
            speed = profile.maxSpeed() * 0.85;
        } else if (distance > contact) {
            speed = profile.maxSpeed() * 0.35;
        } else {
            speed = profile.maxSpeed() * 0.05;
            Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
            lateral = side.scale(this.slipDirection(target) * profile.maxSpeed() * 0.45 * profile.lateralAuthority());
        }

        this.request(forward.scale(speed).add(lateral), profile);
    }

    public void orbit(LivingEntity target, double radius, int direction, MovementProfile profile) {
        Vec3 fromTarget = horizontal(this.entity.position().subtract(target.position()));
        double distance = fromTarget.length();
        Vec3 outward = distance < 1.0E-4 ? this.facing().scale(-1.0) : fromTarget.normalize();
        Vec3 tangent = new Vec3(-outward.z, 0.0, outward.x).scale(direction);
        double radialError = Mth.clamp((radius - distance) / Math.max(1.0, radius * 0.5), -1.0, 1.0);
        Vec3 radial = outward.scale(radialError * profile.maxSpeed() * 0.8);
        Vec3 along = tangent.scale(profile.maxSpeed() * profile.lateralAuthority());
        this.request(clampLength(along.add(radial), profile.maxSpeed()), profile);
    }

    public void seekPosition(Vec3 position, MovementProfile profile) {
        Vec3 toGoal = horizontal(position.subtract(this.entity.position()));
        double distance = toGoal.length();
        if (distance < 0.25) {
            this.arrest(profile);
        } else {
            double speed = profile.maxSpeed() * Mth.clamp(distance / 1.5, 0.2, 1.0);
            this.fallbackGoal = position;
            this.fallbackSpeed = 1.2;
            this.request(toGoal.normalize().scale(speed), profile);
        }
    }

    public void retreatFrom(LivingEntity target, double preferredDistance, MovementProfile profile) {
        double distance = horizontal(target.position().subtract(this.entity.position())).length();
        if (distance >= preferredDistance) {
            this.arrest(profile);
        } else {
            Vec3 away = this.retreatVector(target);
            double urgency = Mth.clamp((preferredDistance - distance) / preferredDistance, 0.25, 1.0);
            this.request(away.scale(profile.maxSpeed() * urgency), profile);
        }
    }

    public void crossPast(LivingEntity target, int side, MovementProfile profile) {
        this.request(this.crossVector(target, side).scale(profile.maxSpeed()), profile);
    }

    public void arrest(MovementProfile profile) {
        this.request(Vec3.ZERO, profile);
    }

    public void navigateTo(double x, double y, double z, double speed) {
        this.intentThisTick = true;
        if (this.owner != MovementOwner.ATTACK_MOTION) {
            this.owner = MovementOwner.NAVIGATION;
            this.entity.getNavigation().moveTo(x, y, z, speed);
        }
    }

    private void request(Vec3 desired, MovementProfile profile) {
        this.intentThisTick = true;
        this.lastProfile = profile;
        if (this.owner != MovementOwner.ATTACK_MOTION && this.committedTicks <= 0) {
            if (this.fallbackTicks > 0 && this.fallbackGoal != null) {
                this.owner = MovementOwner.NAVIGATION;
                this.entity.getNavigation().moveTo(this.fallbackGoal.x, this.fallbackGoal.y, this.fallbackGoal.z, this.fallbackSpeed);
            } else {
                this.takeSteering();
                this.driveTo(clampLength(desired, profile.maxSpeed()), profile);
            }
        }
    }

    public void beginCommittedMotion(Vec3 direction, double speed, int ticks, double verticalImpulse, double lateCorrection) {
        Vec3 heading = horizontal(direction);
        if (heading.lengthSqr() < 1.0E-4) {
            heading = this.facing();
        }

        heading = heading.normalize();
        this.owner = MovementOwner.ATTACK_MOTION;
        this.entity.getNavigation().stop();
        this.committedVelocity = heading.scale(speed);
        this.committedTicks = Math.max(1, ticks);
        this.committedAirborne = verticalImpulse > 0.0;
        this.committedCorrection = Mth.clamp(lateCorrection, 0.0, 0.25);
        this.committedAim = null;
        this.committedExtension = 0;
        this.steering = this.committedVelocity;
        this.intentThisTick = true;
        this.stalledTicks = 0;
        this.entity
            .setDeltaMovement(this.committedVelocity.x * this.speedFactor, verticalImpulse > 0.0 ? verticalImpulse : this.entity.getDeltaMovement().y, this.committedVelocity.z * this.speedFactor);
        if (verticalImpulse > 0.0) {
            this.entity.setOnGround(false);
            this.entity.hasImpulse = true;
        }
    }

    public void setCommittedAim(@Nullable Vec3 aim) {
        this.committedAim = aim == null ? null : horizontal(aim);
    }

    public boolean isCommitted() {
        return this.committedTicks > 0;
    }

    public void cancelCommitted() {
        this.committedTicks = 0;
        this.committedAirborne = false;
        this.committedExtension = 0;
        this.committedAim = null;
        this.steering = Vec3.ZERO;
        if (this.owner == MovementOwner.ATTACK_MOTION) {
            this.owner = MovementOwner.COMBAT_STEERING;
        }

        this.entity.setDeltaMovement(0.0, this.entity.getDeltaMovement().y, 0.0);
    }

    private void tickCommitted() {
        this.committedTicks--;
        if (this.committedCorrection > 0.0 && this.committedAim != null) {
            Vec3 toAim = this.committedAim.subtract(this.entity.position());
            toAim = horizontal(toAim);
            if (toAim.lengthSqr() > 1.0E-4) {
                double speed = this.committedVelocity.length();
                Vec3 blended = this.committedVelocity.normalize().scale(1.0 - this.committedCorrection).add(toAim.normalize().scale(this.committedCorrection));
                if (blended.lengthSqr() > 1.0E-4) {
                    this.committedVelocity = blended.normalize().scale(speed);
                }
            }
        }

        if (this.stalledTicks >= 3 && !this.committedAirborne) {
            this.cancelCommitted();
        } else {
            this.steering = this.committedVelocity;
            this.entity.getNavigation().stop();
            Vec3 velocity = this.limitApproach(this.committedVelocity.scale(this.speedFactor));
            this.entity.setDeltaMovement(velocity.x, this.entity.getDeltaMovement().y, velocity.z);
            this.entity.hasImpulse = true;
            if (this.committedTicks <= 0) {
                if (this.committedAirborne && !this.entity.onGround() && this.committedExtension < 20) {
                    this.committedExtension++;
                    this.committedTicks = 1;
                    return;
                }

                this.committedAirborne = false;
                this.committedExtension = 0;
                this.committedAim = null;
                this.steering = Vec3.ZERO;
                this.entity.setDeltaMovement(0.0, this.entity.getDeltaMovement().y, 0.0);
                if (this.owner == MovementOwner.ATTACK_MOTION) {
                    this.owner = MovementOwner.COMBAT_STEERING;
                }
            }
        }
    }

    public void claimAttackMotion() {
        this.owner = MovementOwner.ATTACK_MOTION;
        this.entity.getNavigation().stop();
    }

    public void releaseToNavigation() {
        this.committedTicks = 0;
        this.committedAirborne = false;
        this.committedExtension = 0;
        this.committedAim = null;
        this.owner = MovementOwner.NAVIGATION;
        this.steering = Vec3.ZERO;
        this.fallbackTicks = 0;
        this.fallbackGoal = null;
        this.stalledTicks = 0;
    }

    public void releaseAttackMotion() {
        this.committedTicks = 0;
        this.committedAirborne = false;
        this.committedExtension = 0;
        this.committedAim = null;
        if (this.owner == MovementOwner.ATTACK_MOTION) {
            this.owner = MovementOwner.COMBAT_STEERING;
        }
    }

    public void applyAttackMotion(Vec3 motion) {
        this.attackMotion = this.attackMotion.add(horizontal(motion));
        this.intentThisTick = true;
        if (this.committedTicks <= 0) {
            this.driveTo(Vec3.ZERO, MovementProfile.HARD_STOP);
        }
    }

    private void driveTo(Vec3 desired, MovementProfile profile) {
        Vec3 delta = desired.subtract(this.steering);
        double gap = delta.length();
        if (gap < 1.0E-6) {
            this.steering = desired;
        } else {
            boolean slowing = desired.lengthSqr() < this.steering.lengthSqr();
            if (slowing) {
                if (gap < profile.snap()) {
                    this.steering = desired;
                } else {
                    this.steering = this.steering.add(delta.scale(profile.arrest()));
                }
            } else {
                double step = Math.max(gap * profile.accel(), Math.min(gap, profile.launchFloor()));
                this.steering = this.steering.add(delta.scale(step / gap));
            }
        }
    }

    private void writeVelocity() {
        if (this.impulseTicks > 0) {
            this.steering = horizontal(this.entity.getDeltaMovement());
        } else {
            Vec3 combined = this.limitApproach(this.steering.add(this.attackMotion).scale(this.speedFactor));
            this.entity.setDeltaMovement(combined.x, this.entity.getDeltaMovement().y, combined.z);
            this.entity.hasImpulse = true;
        }
    }

    /**
     * Cuts the part of a horizontal velocity that would bring the renegade closer to its target than MIN_TARGET_GAP
     * between the hitboxes; sideways and backwards movement stays untouched.
     */
    private Vec3 limitApproach(Vec3 velocity) {
        LivingEntity target = this.entity.getTarget();
        if (target == null) {
            return velocity;
        }

        Vec3 toTarget = horizontal(target.position().subtract(this.entity.position()));
        double distance = toTarget.length();
        if (distance < 1.0E-4) {
            return velocity;
        }

        Vec3 direction = toTarget.scale(1.0 / distance);
        double approach = velocity.x * direction.x + velocity.z * direction.z;
        double allowed = Math.max(0.0, distance - (this.entity.getBbWidth() + target.getBbWidth()) * 0.5 - MIN_TARGET_GAP);
        if (approach <= allowed) {
            return velocity;
        }
        return velocity.subtract(direction.scale(approach - allowed));
    }

    private void takeSteering() {
        if (this.owner != MovementOwner.COMBAT_STEERING) {
            this.owner = MovementOwner.COMBAT_STEERING;
        }

        if (!this.entity.getNavigation().isDone()) {
            this.entity.getNavigation().stop();
        }
    }

    private void trackProgress() {
        Vec3 position = this.entity.position();
        double travelled = position.distanceToSqr(this.previousPosition);
        this.previousPosition = position;
        boolean wantsToMove = this.steering.lengthSqr() > 0.004;
        if (this.owner == MovementOwner.COMBAT_STEERING && wantsToMove && travelled < 0.0016) {
            this.stalledTicks++;
            if (this.stalledTicks >= 6 && this.fallbackGoal != null) {
                this.fallbackTicks = 30;
                this.stalledTicks = 0;
                this.steering = Vec3.ZERO;
            }
        } else if (this.committedTicks > 0 && travelled < 0.0016) {
            this.stalledTicks++;
        } else {
            this.stalledTicks = 0;
        }
    }

    public void setFallbackGoal(@Nullable Vec3 goal, double speed) {
        this.fallbackGoal = goal;
        this.fallbackSpeed = speed;
    }

    public void notifyExternalImpulse(int ticks) {
        this.impulseTicks = Math.max(this.impulseTicks, ticks);
    }

    public void faceTarget(LivingEntity target, float maxDegrees) {
        this.facePosition(target.getX(), target.getZ(), maxDegrees);
    }

    public void facePosition(double x, double z, float maxDegrees) {
        if (!(maxDegrees <= 0.0F)) {
            double dx = x - this.entity.getX();
            double dz = z - this.entity.getZ();
            if (!(Math.abs(dx) < 1.0E-6) || !(Math.abs(dz) < 1.0E-6)) {
                float desired = (float)(Mth.atan2(dz, dx) * 180.0F / (float)Math.PI) - 90.0F;
                float step = Mth.clamp(Mth.wrapDegrees(desired - this.entity.getYRot()), -maxDegrees, maxDegrees);
                float yaw = Mth.wrapDegrees(this.entity.getYRot() + step);
                this.entity.setYRot(yaw);
                this.entity.setYBodyRot(yaw);
                this.entity.setYHeadRot(yaw);
            }
        }
    }

    public void snapFacing(Vec3 direction) {
        Vec3 heading = horizontal(direction);
        if (!(heading.lengthSqr() < 1.0E-4)) {
            float yaw = (float)(Mth.atan2(heading.z, heading.x) * 180.0F / (float)Math.PI) - 90.0F;
            this.entity.setYRot(yaw);
            this.entity.setYBodyRot(yaw);
            this.entity.setYHeadRot(yaw);
        }
    }

    public MovementOwner owner() {
        return this.owner;
    }

    public Vec3 steering() {
        return this.steering;
    }

    public double speed() {
        return this.steering.length();
    }

    public MovementProfile lastProfile() {
        return this.lastProfile;
    }

    public Vec3 facing() {
        Vec3 look = horizontal(this.entity.getLookAngle());
        return look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
    }

    public Vec3 retreatVector(LivingEntity target) {
        Vec3 away = horizontal(this.entity.position().subtract(target.position()));
        if (away.lengthSqr() < 1.0E-4) {
            away = this.facing().scale(-1.0);
        }

        away = away.normalize();
        Vec3 side = new Vec3(-away.z, 0.0, away.x).scale(this.slipDirection(target));
        return away.scale(0.78).add(side.scale(0.62)).normalize();
    }

    public Vec3 crossVector(LivingEntity target, int side) {
        Vec3 toTarget = horizontal(target.position().subtract(this.entity.position()));
        if (toTarget.lengthSqr() < 1.0E-4) {
            return this.facing();
        }

        Vec3 forward = toTarget.normalize();
        Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x).scale(side);
        return forward.scale(0.82).add(lateral.scale(0.57)).normalize();
    }

    public int slipDirection(LivingEntity target) {
        Vec3 toTarget = horizontal(target.position().subtract(this.entity.position()));
        if (toTarget.lengthSqr() < 1.0E-4) {
            return 1;
        } else {
            Vec3 forward = toTarget.normalize();
            Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
            double drift = this.steering.dot(side);
            if (Math.abs(drift) > 0.015) {
                return drift > 0.0 ? 1 : -1;
            } else {
                return (this.entity.getId() & 1) == 0 ? 1 : -1;
            }
        }
    }

    public float angleTo(LivingEntity target) {
        double dx = target.getX() - this.entity.getX();
        double dz = target.getZ() - this.entity.getZ();
        float desired = (float)(Mth.atan2(dz, dx) * 180.0F / (float)Math.PI) - 90.0F;
        return Math.abs(Mth.wrapDegrees(desired - this.entity.getYRot()));
    }

    public static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0.0, vector.z);
    }

    public static Vec3 clampLength(Vec3 vector, double max) {
        double lengthSq = vector.lengthSqr();
        return lengthSq > max * max && lengthSq > 0.0 ? vector.scale(max / Math.sqrt(lengthSq)) : vector;
    }
}
