// Built on the combat system ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0); the attacks are new,
// modelled on the spells of Berserker RPG (only their runtime ids are used).
package com.drehverschluss.renegades.combat.brain;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.combat.ActionContext;
import com.drehverschluss.renegades.combat.AreaStrike;
import com.drehverschluss.renegades.combat.AttackMotion;
import com.drehverschluss.renegades.combat.AttackPhase;
import com.drehverschluss.renegades.combat.CombatAction;
import com.drehverschluss.renegades.combat.GroundLocomotion;
import com.drehverschluss.renegades.combat.LeapPath;
import com.drehverschluss.renegades.combat.MovementProfile;
import com.drehverschluss.renegades.combat.SlashVisual;
import com.drehverschluss.renegades.combat.TrackingMode;
import com.drehverschluss.renegades.combat.geometry.AttackPlane;
import com.drehverschluss.renegades.combat.geometry.AttackVolume;
import com.drehverschluss.renegades.combat.geometry.SwingDynamics;
import com.drehverschluss.renegades.combat.geometry.SwingPath;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.BerserkerRenegade;
import com.drehverschluss.renegades.magic.AbilityPresentation;
import com.drehverschluss.renegades.magic.AbilitySpec;
import com.drehverschluss.renegades.magic.CastTarget;
import com.drehverschluss.renegades.magic.ExternalEffects;
import com.drehverschluss.renegades.magic.MagicParticles;
import com.drehverschluss.renegades.magic.MagicSchool;
import com.drehverschluss.renegades.magic.TickScheduler;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Berserker combat: always presses forward and swings its two-handed axe; no parry, block, dodge or retreat.
 * <ul>
 * <li>rage (BerserkerRenegade#rage): below 50 % health more damage and speed and shorter attack cooldowns;</li>
 * <li>bloody strike: a heavy overhead blow (× 1.3) that makes the target bleed; on a hit the berserker hurts itself for
 * 10 % of the damage and gets blood sacrifice (absorption);</li>
 * <li>apprehend: a wide lunging blow (× 0.6) that pulls the targets it hits up to it and lowers their armor;</li>
 * <li>guillotine: an announced leap at a target 5-10 blocks away that lands on the marked spot with a finishing blow
 * (× 1.5, +20 % per harmful effect on the target, at most +60 %);</li>
 * <li>second wind: once per life, below 30 % health, blood reckoning (absorption for missing health, part of it heals
 * when it ends).</li>
 * </ul>
 */
public class BerserkerBrain extends MobBrain {
    public static final String SWING_A = "animation.mob.swing1_2h";
    public static final String SWING_B = "animation.mob.swing2_2h";
    public static final String BLOODY_STRIKE = "animation.mob.heavy.slam";
    public static final String APPREHEND = "animation.valkyrie.dash_attack";
    public static final String GUILLOTINE = "animation.mob.jugg.leapslam";
    public static final String ROAR = "animation.mob.prepare";
    /**
     * All triggered animations of this brain; registered on the renegade's action controller on both sides.
     */
    public static final List<String> ACTION_ANIMATIONS = List.of(SWING_A, SWING_B, BLOODY_STRIKE, APPREHEND, GUILLOTINE, ROAR);

    private static final float MELEE_THRESHOLD = 4.0F;
    private static final float ENGAGEMENT_THRESHOLD = 12.0F;
    private static final float FLEE_THRESHOLD = 16.0F;
    private static final float DAMAGE_EXCEEDED_THRESHOLD = 12.0F;
    private static final double PREFERRED_RANGE = 2.4;
    private static final float SWING_RANGE = 3.8F;
    // Rage shortens all attack cooldowns by up to this fraction.
    private static final double RAGE_COOLDOWN = 0.2;

    private static final float BLOODY_DAMAGE = 1.3F;
    private static final int BLEED_TICKS = 100;
    private static final float SELF_DAMAGE = 0.1F;
    private static final float SELF_DAMAGE_FLOOR = 2.0F;
    private static final int SACRIFICE_TICKS = 200;
    // Blood sacrifice level 2: max absorption +8 (berserker_rpg's effect config, +4 per level), absorption 4.
    private static final int SACRIFICE_AMPLIFIER = 1;
    private static final float SACRIFICE_ABSORPTION = 4.0F;

    private static final float APPREHEND_DAMAGE = 0.6F;
    private static final double APPREHEND_RANGE = 5.0;
    private static final int APPREHEND_TICKS = 120;
    private static final double PULL_STOP = 1.4;
    private static final int PULL_TICKS = 6;

    private static final float GUILLOTINE_DAMAGE = 1.5F;
    private static final float GUILLOTINE_BONUS_PER_EFFECT = 0.2F;
    private static final float GUILLOTINE_MAX_BONUS = 0.6F;
    private static final double GUILLOTINE_MIN = 5.0;
    private static final double GUILLOTINE_MAX = 10.0;
    private static final double GUILLOTINE_RADIUS = 2.0;
    private static final int GUILLOTINE_WINDUP = 15;
    private static final int GUILLOTINE_FLIGHT = 14;
    private static final double GUILLOTINE_LIFT = 0.588;
    private static final double GUILLOTINE_APEX = 2.4;
    private static final int GUILLOTINE_CHECK_INTERVAL = 10;

    private static final float SECOND_WIND_BELOW = 0.3F;
    private static final int RECKONING_TICKS = 160;

    private static final String SWING_ID = "berserker_swing";
    private static final String BLOODY_ID = "berserker_bloody_strike";
    private static final String APPREHEND_ID = "berserker_apprehend";
    private static final String GUILLOTINE_ID = "berserker_guillotine";
    private static final String GUILLOTINE_CHECK = "berserker_guillotine_check";

    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.7F, 0.05F, 0.05F), 1.4F);
    private static final DustParticleOptions RAGE = new DustParticleOptions(new Vector3f(0.9F, 0.15F, 0.05F), 1.0F);

    private final BerserkerRenegade berserker;
    private final Holder<MobEffect> bleed;
    private final Holder<MobEffect> bloodSacrifice;
    private final Holder<MobEffect> apprehended;
    private final Holder<MobEffect> bloodReckoning;
    // Abilities only for their look (caster_visuals keys); the attacks themselves are combat actions.
    private final AbilitySpec rageLook;
    private final AbilitySpec bloodyLook;
    private final AbilitySpec apprehendLook;
    private final AbilitySpec guillotineLook;
    private final AbilitySpec secondWindLook;
    private final CombatAction swingA;
    private final CombatAction swingB;
    private final CombatAction bloodyStrike;
    private final CombatAction apprehend;
    private final CombatAction guillotine;
    private boolean nextSwingA = true;
    @Nullable
    private Vec3 guillotineSpot;
    private boolean guillotineLaunched;
    private float selfDamageTotal;

    public BerserkerBrain(BerserkerRenegade entity) {
        super(entity, Stance.FRENZY, Stance.FRENZY);
        this.berserker = entity;
        this.bleed = ExternalEffects.orElse("spell_engine:bleed", MobEffects.POISON);
        this.bloodSacrifice = ExternalEffects.orElse("berserker_rpg:blood_sacrifice", MobEffects.ABSORPTION);
        this.apprehended = ExternalEffects.orElse("berserker_rpg:apprehend", MobEffects.WEAKNESS);
        this.bloodReckoning = ExternalEffects.orElse("berserker_rpg:blood_reckoning", MobEffects.ABSORPTION);
        this.rageLook = look("rage");
        this.bloodyLook = look("bloody_strike");
        this.apprehendLook = look("apprehend");
        this.guillotineLook = look("guillotine");
        this.secondWindLook = look("second_wind");

        this.swingA = this.swing(SWING_A, SwingPath.rightToLeft(150.0F));
        this.swingB = this.swing(SWING_B, SwingPath.leftToRight(150.0F));
        this.bloodyStrike = CombatAction.builder(BLOODY_ID)
                .animation(BLOODY_STRIKE)
                .timing(15, 4, 10)
                .motion(AttackMotion.PRESSING)
                .windupDrift(0.2)
                .advance(0.8)
                .tracking(TrackingMode.REDUCED, TrackingMode.LOCKED)
                .cooldown(100)
                .volume(AttackVolume.sweep(SwingPath.overhead(70.0F, AttackPlane.VERTICAL).withDynamics(SwingDynamics.FORCEFUL), 4.0, 3.0))
                .scaledDamage(BLOODY_DAMAGE)
                .knockback(0.4, 0.1)
                .slash(SlashVisual.heavy(BLOOD))
                .onStart(ctx -> {
                    ctx.entity().playSound(SoundEvents.RAVAGER_ROAR, 0.6F, 1.2F);
                    this.spellWindup(this.bloodyLook);
                })
                .onHit(this::onBloodyHit)
                .build();
        // The swing's area is fixed when it starts, so the lunge happens in the windup (about 3 blocks + reach 5 = 8).
        this.apprehend = CombatAction.builder(APPREHEND_ID)
                .animation(APPREHEND)
                .timing(10, 5, 9)
                .motion(AttackMotion.PRESSING)
                .windupDrift(3.6)
                .advance(0.5)
                .tracking(TrackingMode.FULL, TrackingMode.LOCKED)
                .cooldown(240)
                .volume(AttackVolume.sweep(SwingPath.leftToRight(120.0F).withDynamics(SwingDynamics.FORCEFUL), APPREHEND_RANGE, 2.8))
                .scaledDamage(APPREHEND_DAMAGE)
                .knockback(0.0, 0.0)
                .slash(SlashVisual.light(ParticleTypes.SWEEP_ATTACK).withCount(6))
                .onStart(ctx -> this.spellWindup(this.apprehendLook))
                .onActiveStart(ctx -> {
                    if (this.entity.level() instanceof ServerLevel level
                            && !AbilityPresentation.spellRelease(level, this.entity, this.apprehendLook, CastTarget.at(this.entity, this.entity.position())).sounds()) {
                        this.entity.playSound(SoundEvents.CHAIN_PLACE, 1.2F, 0.6F);
                    }
                })
                .onHit(this::onApprehendHit)
                .build();
        // Like the paladin's leap: no lift here, launchGuillotine decides at take-off whether the berserker really jumps.
        this.guillotine = CombatAction.builder(GUILLOTINE_ID)
                .animation(GUILLOTINE)
                .timing(GUILLOTINE_WINDUP, GUILLOTINE_FLIGHT, 12)
                .motion(AttackMotion.COMMITTED_VECTOR)
                .committed(0.5, GUILLOTINE_FLIGHT, 0.0, 0.0)
                .tracking(TrackingMode.FULL, TrackingMode.LOCKED)
                .cooldown(360)
                .onStart(this::announceGuillotine)
                .onActiveStart(this::launchGuillotine)
                .onRecoveryStart(ctx -> {
                    if (this.guillotineLaunched) {
                        this.landGuillotine();
                    }
                })
                .onFinish(ctx -> {
                    this.guillotineLaunched = false;
                    this.guillotineSpot = null;
                })
                .build();
    }

    private static AbilitySpec look(String key) {
        return AbilitySpec.builder(ResourceLocation.fromNamespaceAndPath(Renegades.MODID, "berserker/" + key), MagicSchool.PHYSICAL_MELEE)
                .visual(key)
                .build();
    }

    private CombatAction swing(String animation, SwingPath path) {
        return CombatAction.builder(SWING_ID)
                .animation(animation)
                .timing(5, 4, 6)
                .motion(AttackMotion.PRESSING)
                .windupDrift(0.2)
                .advance(0.6)
                .tracking(TrackingMode.REDUCED, TrackingMode.LOCKED)
                .cooldown(48)
                .volume(AttackVolume.sweep(path.withDynamics(SwingDynamics.FORCEFUL), SWING_RANGE, 2.8))
                .weaponDamage()
                .knockback(0.35, 0.08)
                .slash(SlashVisual.light(ParticleTypes.CRIT).withCount(14))
                .build();
    }

    @Override
    protected AIStimulus createStimulus() {
        return new AIStimulus(this.entity, MELEE_THRESHOLD, ENGAGEMENT_THRESHOLD, FLEE_THRESHOLD, DAMAGE_EXCEEDED_THRESHOLD);
    }

    @Override
    protected List<Transition> stanceTransitions() {
        return List.of();
    }

    @Override
    protected List<Transition> combatStateTransitions() {
        return List.of();
    }

    @Override
    protected List<PhaseTransition> definePhaseTransitions() {
        return List.of();
    }

    @Override
    protected CombatAction reachAction() {
        return this.swingA;
    }

    @Override
    protected void executeCurrentCombatState() {
        LivingEntity target = this.entity.getTarget();
        this.tickRage();
        this.trySecondWind();
        if (target == null || this.actionOwnsMovement() || this.handleUnreachableTarget(target) || this.tryChain()) {
            return;
        }

        float rage = this.berserker.rage();
        MovementProfile profile = MovementProfile.RELENTLESS.urgent(1.0 + rage * 0.15, 1.0 + rage * 0.3, 1.0F + rage * 0.3F);
        this.locomotion.faceTarget(target, profile.turnDegrees());
        float distance = this.stimulus.targetDistance;
        if (distance <= FLEE_THRESHOLD && this.stimulus.lineOfSightToTarget) {
            this.locomotion.setFallbackGoal(target.position(), 1.2);
            this.locomotion.pressure(target, PREFERRED_RANGE, profile);
        } else {
            this.locomotion.navigateTo(target.getX(), target.getY(), target.getZ(), 1.2);
        }

        if (this.actions.isBusy() || !this.stimulus.lineOfSightToTarget) {
            return;
        }
        if (distance >= GUILLOTINE_MIN && this.canGuillotine(target)) {
            this.startFull(this.guillotine);
        } else if (distance >= 4.0F && distance <= 8.0F && this.cooldowns.isReady(APPREHEND_ID) && this.withinReach(target, this.apprehend)) {
            this.startFull(this.apprehend);
        } else if (distance <= 4.2F && this.cooldowns.isReady(BLOODY_ID) && this.canStrike(target, this.bloodyStrike)) {
            this.startFull(this.bloodyStrike);
        } else if (distance <= SWING_RANGE + 0.6F && this.cooldowns.isReady(SWING_ID) && this.canStrike(target, this.swingA)) {
            this.nextSwingA = !this.nextSwingA;
            this.startAction(this.nextSwingA ? this.swingA : this.swingB, this.rageCooldownScale());
        }
    }

    /**
     * Height check of the volume only (canStrike also wants the target within the volume's range, but apprehend lunges
     * in first).
     */
    private boolean withinReach(LivingEntity target, CombatAction action) {
        return action.volume() != null && withinVerticalReach(this.entity, target, action.volume());
    }

    private double rageCooldownScale() {
        return 1.0 - RAGE_COOLDOWN * this.berserker.rage();
    }

    /**
     * Abilities keep their cooldown (only rage shortens it), unlike the usual random shortening.
     */
    private void startFull(CombatAction action) {
        if (this.startAction(action)) {
            this.cooldowns.trigger(action.id(), (int) (action.cooldownTicks() * this.rageCooldownScale()));
        }
    }

    /**
     * Rage particles: from 50 % health on red particles around the berserker, more and more often the lower its health;
     * with an assigned rage look, that spell's release burst on top every few seconds.
     */
    private void tickRage() {
        float rage = this.berserker.rage();
        if (rage <= 0.0F || !(this.entity.level() instanceof ServerLevel level)) {
            return;
        }
        int interval = Math.max(4, Math.round(16.0F - 12.0F * rage));
        if (this.entity.tickCount % interval == 0) {
            Vec3 at = this.entity.position();
            level.sendParticles(RAGE, at.x, at.y + 1.0, at.z, 3 + Math.round(9 * rage), 0.35, 0.6, 0.35, 0.0);
            if (rage > 0.5F && this.entity.tickCount % (interval * 4) == 0) {
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, at.x, at.y + 2.1, at.z, 1, 0.2, 0.1, 0.2, 0.0);
            }
        }
        if (this.entity.tickCount % Math.max(20, Math.round(60.0F - 40.0F * rage)) == 0) {
            AbilityPresentation.spellRelease(level, this.entity, this.rageLook, CastTarget.at(this.entity, this.entity.position()));
        }
    }

    /**
     * Second wind: once per life below 30 % health, blood reckoning (the effect gives absorption for missing health and
     * heals part of the remaining absorption when it ends).
     */
    private void trySecondWind() {
        if (this.berserker.isSecondWindUsed() || this.entity.getHealth() >= this.entity.getMaxHealth() * SECOND_WIND_BELOW
                || !(this.entity.level() instanceof ServerLevel level)) {
            return;
        }
        this.berserker.markSecondWindUsed();
        this.entity.addEffect(new MobEffectInstance(this.bloodReckoning, RECKONING_TICKS, 0), this.entity);
        if (!this.actions.isBusy()) {
            this.entity.playActionAnimation(com.drehverschluss.renegades.entity.RenegadeEntity.ACTION_CONTROLLER, ROAR);
        }
        AbilityPresentation.Handled handled = AbilityPresentation.spellRelease(level, this.entity, this.secondWindLook,
                CastTarget.at(this.entity, this.entity.position()));
        if (!handled.particles()) {
            MagicParticles.pulse(level, MagicSchool.FIRE, this.entity.getBoundingBox().getCenter(), 1.6F);
        }
        if (!handled.sounds()) {
            this.entity.playSound(SoundEvents.RAVAGER_ROAR, 1.2F, 0.8F);
        }
    }

    /**
     * Bloody strike hit: bleed on the target; the berserker pays 10 % of the damage dealt with its own health (never
     * below one heart, then nothing) and gets blood sacrifice with some absorption for it.
     */
    private void onBloodyHit(ActionContext ctx, LivingEntity victim, float dealt) {
        victim.addEffect(new MobEffectInstance(this.bleed, BLEED_TICKS, 0), this.entity);
        if (this.entity.level() instanceof ServerLevel level && !AbilityPresentation.spellImpact(level, this.entity, this.bloodyLook, victim).particles()) {
            Vec3 at = victim.getBoundingBox().getCenter();
            level.sendParticles(BLOOD, at.x, at.y, at.z, 16, 0.3, 0.4, 0.3, 0.0);
        }
        float self = dealt * SELF_DAMAGE;
        if (self <= 0.0F || this.entity.getHealth() - self < SELF_DAMAGE_FLOOR) {
            return;
        }
        this.entity.setHealth(this.entity.getHealth() - self);
        this.selfDamageTotal += self;
        // The effect raises max absorption first, so the absorption set right after is not capped.
        this.entity.addEffect(new MobEffectInstance(this.bloodSacrifice, SACRIFICE_TICKS, SACRIFICE_AMPLIFIER), this.entity);
        this.entity.setAbsorptionAmount(Math.max(this.entity.getAbsorptionAmount(), SACRIFICE_ABSORPTION));
    }

    /**
     * Apprehend hit: lowers the target's armor and pulls it up to the berserker, at most to the last safe spot on the
     * way (no lava, fire or drop, no wall), never through or past it.
     */
    private void onApprehendHit(ActionContext ctx, LivingEntity victim, float dealt) {
        victim.addEffect(new MobEffectInstance(this.apprehended, APPREHEND_TICKS, 0), this.entity);
        if (this.entity.level() instanceof ServerLevel level) {
            AbilityPresentation.spellImpact(level, this.entity, this.apprehendLook, victim);
            Vec3 destination = this.pullDestination(victim);
            if (destination != null) {
                this.pull(level, victim, destination, 0);
            }
        }
    }

    @Nullable
    private Vec3 pullDestination(LivingEntity victim) {
        Vec3 here = this.entity.position();
        Vec3 away = GroundLocomotion.horizontal(victim.position().subtract(here));
        double distance = away.length();
        if (distance <= PULL_STOP + 0.2) {
            return null;
        }
        Vec3 direction = away.scale(1.0 / distance);
        Vec3 end = here.add(direction.scale(PULL_STOP));
        Vec3 best = null;
        int steps = Mth.ceil((distance - PULL_STOP) * 2.0);
        for (int i = 1; i <= steps; i++) {
            Vec3 point = victim.position().lerp(new Vec3(end.x, victim.getY(), end.z), (double) i / steps);
            if (!GroundLocomotion.isSafeStandingSpot(victim.level(), point)
                    || !victim.level().noCollision(victim, victim.getBoundingBox().move(point.subtract(victim.position())))) {
                break;
            }
            best = point;
        }
        return best;
    }

    /**
     * Moves the victim towards the destination over a few ticks by its velocity (also for players, so the client moves
     * them smoothly and no flying check triggers).
     */
    private void pull(ServerLevel level, LivingEntity victim, Vec3 destination, int step) {
        if (!victim.isAlive() || step >= PULL_TICKS) {
            if (victim.isAlive()) {
                victim.setDeltaMovement(0.0, victim.getDeltaMovement().y, 0.0);
                victim.hurtMarked = true;
            }
            return;
        }
        Vec3 left = GroundLocomotion.horizontal(destination.subtract(victim.position()));
        if (left.length() < 0.3) {
            victim.setDeltaMovement(0.0, victim.getDeltaMovement().y, 0.0);
            victim.hurtMarked = true;
            return;
        }
        Vec3 velocity = left.scale(0.45);
        victim.setDeltaMovement(velocity.x, Math.max(victim.getDeltaMovement().y, 0.0), velocity.z);
        victim.hurtMarked = true;
        victim.resetFallDistance();
        TickScheduler.schedule(level, 1, () -> this.pull(level, victim, destination, step + 1));
    }

    private boolean canGuillotine(LivingEntity target) {
        if (!RenegadesConfig.ENABLE_GUILLOTINE.get() || !this.cooldowns.isReady(GUILLOTINE_ID) || !this.cooldowns.isReady(GUILLOTINE_CHECK)) {
            return false;
        }
        this.cooldowns.trigger(GUILLOTINE_CHECK, GUILLOTINE_CHECK_INTERVAL);
        return target.onGround() && this.isSafeGuillotine(target.position());
    }

    private boolean isSafeGuillotine(Vec3 destination) {
        return LeapPath.isSafe(this.entity, destination, GUILLOTINE_MIN - 0.5, GUILLOTINE_MAX + 0.5, GUILLOTINE_APEX, 1.5, 3);
    }

    /**
     * The landing spot is fixed when the windup starts and marked on the ground, so the target can step away.
     */
    private void announceGuillotine(ActionContext ctx) {
        this.guillotineLaunched = false;
        LivingEntity target = ctx.liveTarget();
        this.guillotineSpot = target == null ? null : target.position();
        this.entity.playSound(SoundEvents.RAVAGER_ROAR, 1.4F, 0.7F);
        this.spellWindup(this.guillotineLook);
        if (this.guillotineSpot != null && this.entity.level() instanceof ServerLevel level) {
            this.markSpot(level, this.guillotineSpot);
            for (int delay = 4; delay < GUILLOTINE_WINDUP; delay += 4) {
                Vec3 spot = this.guillotineSpot;
                TickScheduler.schedule(level, delay, () -> {
                    if (this.entity.isAlive() && this.actions.current() == this.guillotine && this.actions.phase() == AttackPhase.WINDUP) {
                        this.markSpot(level, spot);
                    }
                });
            }
        }
    }

    private void markSpot(ServerLevel level, Vec3 spot) {
        MagicParticles.ring(level, MagicSchool.FIRE, spot, (float) GUILLOTINE_RADIUS);
        level.sendParticles(BLOOD, spot.x, spot.y + 0.1, spot.z, 10, GUILLOTINE_RADIUS * 0.4, 0.05, GUILLOTINE_RADIUS * 0.4, 0.0);
    }

    private void launchGuillotine(ActionContext ctx) {
        Vec3 spot = this.guillotineSpot;
        if (spot == null || !this.isSafeGuillotine(spot)) {
            // The spot became unsafe during the windup (or the target was lost): no jump.
            this.locomotion.cancelCommitted();
            return;
        }
        Vec3 toSpot = GroundLocomotion.horizontal(spot.subtract(this.entity.position()));
        double distance = Mth.clamp(toSpot.length(), 2.0, GUILLOTINE_MAX + 0.5);
        double speed = Math.min(1.05, distance / GUILLOTINE_FLIGHT);
        ctx.locomotion().beginCommittedMotion(toSpot, speed, GUILLOTINE_FLIGHT, GUILLOTINE_LIFT, 0.0);
        this.entity.playSound(SoundEvents.RAVAGER_STEP, 1.2F, 0.6F);
        this.guillotineLaunched = true;
    }

    /**
     * Finishing blow where the berserker lands: × 1.5, +20 % per harmful effect on the victim (bleed, apprehend, ...),
     * at most +60 %.
     */
    private void landGuillotine() {
        if (!(this.entity.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 centre = this.entity.position();
        AbilityPresentation.Handled handled = AbilityPresentation.spellRelease(level, this.entity, this.guillotineLook, CastTarget.at(this.entity, centre));
        for (LivingEntity victim : AreaStrike.victims(this.entity, centre, GUILLOTINE_RADIUS, 2.5)) {
            long harmful = victim.getActiveEffects().stream().filter(effect -> effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL).count();
            float bonus = Math.min(GUILLOTINE_MAX_BONUS, harmful * GUILLOTINE_BONUS_PER_EFFECT);
            if (this.entity.meleeHit(victim, GUILLOTINE_DAMAGE * (1.0F + bonus))) {
                AbilityPresentation.spellImpact(level, this.entity, this.guillotineLook, victim);
            }
        }
        if (!handled.particles()) {
            level.sendParticles(ParticleTypes.CRIT, centre.x, centre.y + 0.5, centre.z, 30, 1.0, 0.3, 1.0, 0.2);
            level.sendParticles(BLOOD, centre.x, centre.y + 0.2, centre.z, 20, 1.0, 0.1, 1.0, 0.0);
        }
        if (!handled.sounds()) {
            this.entity.playSound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.3F, 0.8F);
        }
    }

    private void spellWindup(AbilitySpec look) {
        if (this.entity.level() instanceof ServerLevel level) {
            AbilityPresentation.spellWindup(level, this.entity, look);
        }
    }

    public boolean isGuillotineLeaping() {
        return this.guillotineLaunched && this.actions.current() == this.guillotine;
    }

    @Override
    public void onTargetLost() {
        super.onTargetLost();
        this.guillotineSpot = null;
    }

    @Override
    public String describe() {
        return super.describe() + String.format(Locale.ROOT, ", rage %.0f %% (damage +%.0f %%, speed +%.0f %%), second wind %s, self damage %.1f, absorption %.1f",
                this.berserker.rage() * 100.0F, this.berserker.rageModifier(Attributes.ATTACK_DAMAGE) * 100.0,
                this.berserker.rageModifier(Attributes.MOVEMENT_SPEED) * 100.0, this.berserker.isSecondWindUsed() ? "used" : "ready",
                this.selfDamageTotal, this.entity.getAbsorptionAmount());
    }

    public enum Stance implements BrainState {
        FRENZY;

        @Override
        public String id() {
            return this.name();
        }
    }
}
