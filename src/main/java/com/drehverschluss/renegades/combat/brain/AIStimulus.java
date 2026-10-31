// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.brain;

import com.drehverschluss.renegades.entity.RenegadeEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class AIStimulus {
    public boolean targetInMeleeRange;
    public boolean targetInEngagementRange;
    public boolean targetFleeingDistance;
    public float targetDistance;
    public float targetFacingDot;
    public Vec3 targetVelocity = Vec3.ZERO;
    public boolean playerIsBlocking;
    public boolean playerIsCasting;
    public boolean playerJustLanded;
    public boolean playerJustAttacked;
    public int timeSinceLastPlayerAttack = 9999;
    public int timeSincePlayerUsedDefensive = 9999;
    public float playerHealthPct;
    public float ownHealthPct;
    public int timeSinceLastHit = 9999;
    public float lastDamageTaken;
    public boolean lastHitDamageExceeded;
    public int currentExposureDuration;
    public boolean lineOfSightToTarget;
    public int nearbyAllyCount;
    public int nearbyTrapCount;
    public boolean obstructionBetweenSelf;
    public Vec3 lastAmbushVector;
    private boolean prevWasOnGround;
    private float prevVelocityY;
    private float prevAttackCooldown = 1.0F;
    private boolean prevIsBlocking;
    private final RenegadeEntity owner;
    private final float meleeThreshold;
    private final float engagementThreshold;
    private final float fleeThreshold;
    private final float damageExceededThreshold;

    public AIStimulus(RenegadeEntity owner, float meleeThreshold, float engagementThreshold, float fleeThreshold, float damageExceededThreshold) {
        this.owner = owner;
        this.meleeThreshold = meleeThreshold;
        this.engagementThreshold = engagementThreshold;
        this.fleeThreshold = fleeThreshold;
        this.damageExceededThreshold = damageExceededThreshold;
    }

    public void refresh() {
        if (!this.owner.level().isClientSide()) {
            LivingEntity target = this.owner.getTarget();
            if (target != null) {
                this.targetDistance = this.owner.distanceTo(target);
                this.targetInMeleeRange = this.targetDistance <= this.meleeThreshold;
                this.targetInEngagementRange = this.targetDistance <= this.engagementThreshold;
                this.targetFleeingDistance = this.targetDistance >= this.fleeThreshold;
                Vec3 toTarget = target.position().subtract(this.owner.position()).normalize();
                this.targetFacingDot = (float)this.owner.getLookAngle().dot(toTarget);
                this.targetVelocity = target.getDeltaMovement();
                boolean isBlocking = target.isBlocking();
                this.playerIsBlocking = isBlocking;
                if (!this.prevIsBlocking && isBlocking) {
                    this.timeSincePlayerUsedDefensive = 0;
                }

                this.prevIsBlocking = isBlocking;
                this.playerIsCasting = target.isUsingItem() && !isBlocking;
                float currentVelocityY = (float)target.getDeltaMovement().y;
                this.playerJustLanded = !this.prevWasOnGround && target.onGround() && this.prevVelocityY < -0.2F;
                this.prevWasOnGround = target.onGround();
                this.prevVelocityY = currentVelocityY;
                float currentAttackCooldown = 1.0F;
                if (target instanceof Player player) {
                    currentAttackCooldown = player.getAttackStrengthScale(0.0F);
                    this.playerHealthPct = player.getHealth() / player.getMaxHealth();
                } else {
                    this.playerHealthPct = target.getHealth() / Math.max(1.0F, target.getMaxHealth());
                }

                this.playerJustAttacked = this.prevAttackCooldown > 0.7F && currentAttackCooldown < 0.2F;
                if (this.playerJustAttacked) {
                    this.timeSinceLastPlayerAttack = 0;
                } else {
                    this.timeSinceLastPlayerAttack++;
                }

                this.prevAttackCooldown = currentAttackCooldown;
                this.ownHealthPct = this.owner.getHealth() / Math.max(1.0F, this.owner.getMaxHealth());
                this.timeSinceLastHit++;
                if (this.targetInEngagementRange) {
                    this.currentExposureDuration++;
                } else {
                    this.currentExposureDuration = 0;
                }

                if (!this.playerIsBlocking) {
                    this.timeSincePlayerUsedDefensive++;
                }

                this.lineOfSightToTarget = this.owner.hasLineOfSight(target);
                this.nearbyAllyCount = 0;
                this.nearbyTrapCount = 0;
            }
        }
    }

    public void notifyDamage(float amount) {
        this.lastDamageTaken = amount;
        this.lastHitDamageExceeded = amount >= this.damageExceededThreshold;
        this.timeSinceLastHit = 0;
    }
}
