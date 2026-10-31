// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: no swing network packet (ribbons); hits go through RenegadeEntity's vanilla-style melee hit and need line of
// sight from the attacker, so swings never hit through walls.
package com.drehverschluss.renegades.combat;

import com.drehverschluss.renegades.combat.geometry.AttackFrame;
import com.drehverschluss.renegades.combat.geometry.AttackGeometry;
import com.drehverschluss.renegades.combat.geometry.AttackVolume;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the hits of one attack volume during the ACTIVE phase of an action.
 */
public final class SwingExecution {
    private final RenegadeEntity attacker;
    private final CombatAction action;
    private final AttackVolume volume;
    private AttackFrame frame;
    @Nullable
    private Vec3 previousOrigin;
    private float previousProgress;
    private final Map<Entity, Integer> hits = new HashMap<>();
    private boolean weaponSwingSpent;
    private int tick;

    SwingExecution(RenegadeEntity attacker, CombatAction action, AttackVolume volume, AttackFrame frame) {
        this.attacker = attacker;
        this.action = action;
        this.volume = volume;
        this.frame = frame;
        this.previousOrigin = frame.origin();
    }

    public AttackFrame frame() {
        return this.frame;
    }

    void tick(int phaseTick) {
        this.tick = phaseTick;
        if (this.attacker.level() instanceof ServerLevel world) {
            if (this.volume.shape().followsAttacker()) {
                this.previousOrigin = this.frame.origin();
                this.frame = this.frame.withOrigin(this.attacker.position().add(0.0, this.volume.offsetVertical(), 0.0));
            }

            int active = Math.max(1, this.action.activeTicks());
            float progress = Math.min(1.0F, (float) phaseTick / active);
            float from = this.volume.shape().isSwept() ? this.previousProgress : 0.0F;
            float to = this.volume.shape().isSwept() ? progress : 1.0F;
            this.resolveDamage(world, from, to);
            this.drawSlash(world, from, to);
            this.previousProgress = progress;
            if (!this.volume.shape().isSwept() && !this.volume.shape().followsAttacker()) {
                this.previousProgress = 1.0F;
            }
        }
    }

    private void resolveDamage(ServerLevel world, float from, float to) {
        AABB bounds = AttackGeometry.bounds(this.volume, this.frame, this.previousOrigin);

        for (LivingEntity victim : world.getEntitiesOfClass(LivingEntity.class, bounds, this::isValidTarget)) {
            if (this.canHitAgain(victim) && AttackGeometry.hits(this.volume, this.frame, from, to, victim.getBoundingBox(), this.previousOrigin)) {
                this.strike(victim, to);
            }
        }
    }

    private boolean isValidTarget(LivingEntity candidate) {
        return this.attacker.canHarm(candidate) && this.attacker.hasLineOfSight(candidate);
    }

    private boolean canHitAgain(LivingEntity victim) {
        Integer last = this.hits.get(victim);
        return last == null || this.action.multiHit() && this.tick - last >= this.action.multiHitCooldown();
    }

    private void strike(LivingEntity victim, float progress) {
        this.hits.put(victim, this.tick);
        boolean landed;
        if (this.action.usesWeaponDamage() && !this.weaponSwingSpent) {
            this.weaponSwingSpent = true;
            landed = this.attacker.doHurtTarget(victim);
        } else {
            landed = this.attacker.meleeHit(victim, this.action.damageScale());
        }

        if (landed && !(this.action.knockback() <= 0.0)) {
            Vec3 radial = this.frame.origin().subtract(victim.position());
            Vec3 radialDir = radial.horizontalDistanceSqr() < 1.0E-6 ? this.frame.forward().scale(-1.0) : new Vec3(radial.x, 0.0, radial.z).normalize();
            Vec3 strike = AttackGeometry.strikeDirection(this.volume, this.frame, progress);
            double alignment = AttackGeometry.knockbackAlignment(this.volume);
            Vec3 push = radialDir.scale(-(1.0 - alignment)).add(new Vec3(strike.x, 0.0, strike.z).scale(alignment));
            if (push.lengthSqr() < 1.0E-6) {
                push = this.frame.forward();
            }

            push = push.normalize();
            victim.knockback(this.action.knockback(), -push.x, -push.z);
            if (this.action.knockbackVertical() > 0.0) {
                victim.push(0.0, this.action.knockbackVertical(), 0.0);
                victim.hurtMarked = true;
            }
        }
    }

    private void drawSlash(ServerLevel world, float from, float to) {
        SlashVisual visual = this.action.slash();
        if (visual != null && visual.perTick() > 0) {
            RandomSource random = this.attacker.getRandom();

            for (int i = 0; i < visual.perTick(); i++) {
                AttackGeometry.Sample sample = AttackGeometry.sample(this.volume, this.frame, from, to, random, visual.speed(), visual.scatter());
                world.sendParticles(
                        visual.particle(),
                        sample.position().x,
                        sample.position().y,
                        sample.position().z,
                        0,
                        sample.velocity().x,
                        sample.velocity().y,
                        sample.velocity().z,
                        1.0
                );
            }
        }
    }
}
