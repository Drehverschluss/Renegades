// Built on the combat and magic system ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0); the attacks
// are new, modelled on the spells of Forcemaster RPG (only their runtime ids are used).
package com.drehverschluss.renegades.combat.brain;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.combat.ActionContext;
import com.drehverschluss.renegades.combat.AttackMotion;
import com.drehverschluss.renegades.combat.AttackPhase;
import com.drehverschluss.renegades.combat.CombatAction;
import com.drehverschluss.renegades.combat.GroundLocomotion;
import com.drehverschluss.renegades.combat.MovementProfile;
import com.drehverschluss.renegades.combat.SlashVisual;
import com.drehverschluss.renegades.combat.TrackingMode;
import com.drehverschluss.renegades.combat.geometry.AttackFrame;
import com.drehverschluss.renegades.combat.geometry.AttackGeometry;
import com.drehverschluss.renegades.combat.geometry.AttackShape;
import com.drehverschluss.renegades.combat.geometry.AttackVolume;
import com.drehverschluss.renegades.combat.geometry.SwingPath;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.drehverschluss.renegades.magic.AbilityExecutor;
import com.drehverschluss.renegades.magic.AbilityPresentation;
import com.drehverschluss.renegades.magic.AbilitySpec;
import com.drehverschluss.renegades.magic.CastTarget;
import com.drehverschluss.renegades.magic.Delivery;
import com.drehverschluss.renegades.magic.Impact;
import com.drehverschluss.renegades.magic.MagicParticles;
import com.drehverschluss.renegades.magic.MagicSchool;
import com.drehverschluss.renegades.magic.TickScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Forcemaster combat: a combo fighter with knuckles in both hands that keeps moving around its target, charges up with
 * every technique and unloads in one huge announced blow.
 * <ul>
 * <li>fist combo: 2-3 quick jabs left/right (× 0.5 each), finished by an uppercut (× 0.8) that lifts the target;</li>
 * <li>sonic hand: target right in front: 5 very fast punches in under a second (× 0.3 each);</li>
 * <li>belial smashing: a straight rush over about 9 blocks that hits everything on the way once (× 0.7) and pushes it
 * aside; stops at walls and never runs into lava or off a drop;</li>
 * <li>nen sphere: target 6-14 blocks away: charges for a second (a hit of 4+ damage interrupts it), then a piercing
 * arcane beam (× 0.7, magic) that a raised shield blocks from the front;</li>
 * <li>arcane overflow: every technique and every finished combo gives a charge (at most 5), shown at the fists;</li>
 * <li>asal: with 5 charges and the target within 4 blocks, a clearly announced windup (1.1 s, the hit zone is marked),
 * then a huge blow straight ahead (5.5 × 2 blocks, × 3.0) that uses up all charges; afterwards it is exhausted for
 * 2 s (slow, no attacks, no dodging);</li>
 * <li>dodge: a side step when the target strikes at it in melee.</li>
 * </ul>
 */
public class ForcemasterBrain extends MobBrain {
    public static final String JAB_LEFT = "animation.mob.throw1";
    public static final String JAB_RIGHT = "animation.mob.throw2";
    public static final String UPPERCUT = "animation.mob.trickster.pommelstrike";
    public static final String RUSH = "animation.valkyrie.dash_attack";
    public static final String NEN_CHARGE = "animation.v2.mage.cast_long";
    public static final String NEN_RELEASE = "animation.v2.mage.cast_quick";
    public static final String ASAL = "animation.v2.mage.nova";
    public static final String DODGE_LEFT = "animation.v2.trickster.dodge_left";
    public static final String DODGE_RIGHT = "animation.v2.trickster.dodge_right";
    /**
     * All triggered animations of this brain; registered on the renegade's action controller on both sides.
     */
    public static final List<String> ACTION_ANIMATIONS = List.of(JAB_LEFT, JAB_RIGHT, UPPERCUT, RUSH, NEN_CHARGE, NEN_RELEASE, ASAL,
            DODGE_LEFT, DODGE_RIGHT);

    private static final float MELEE_THRESHOLD = 3.0F;
    private static final float ENGAGEMENT_THRESHOLD = 14.0F;
    private static final float FLEE_THRESHOLD = 18.0F;
    private static final float DAMAGE_EXCEEDED_THRESHOLD = 8.0F;
    private static final double PREFERRED_RANGE = 2.2;
    private static final double ORBIT_RADIUS = 2.6;
    private static final float COMBO_RANGE = 3.0F;

    private static final float JAB_DAMAGE = 0.5F;
    private static final float UPPERCUT_DAMAGE = 0.8F;
    private static final double UPPERCUT_LIFT = 0.5;
    private static final int COMBO_REST = 6;
    private static final float SONIC_RANGE = 2.5F;
    private static final float SONIC_DAMAGE = 0.3F;
    private static final int SONIC_HITS = 5;
    private static final int SONIC_INTERVAL = 4;
    private static final float RUSH_DAMAGE = 0.7F;
    private static final double RUSH_MIN = 5.5;
    private static final double RUSH_MAX = 9.0;
    private static final double RUSH_SIDE_PUSH = 0.7;
    private static final float NEN_DAMAGE = 0.7F;
    private static final double NEN_MIN = 6.0;
    private static final double NEN_RANGE = 14.0;
    private static final float NEN_INTERRUPT = 4.0F;
    // A target that keeps this far away for this long gets the nen sphere even inside the rush range.
    private static final double KEEPS_DISTANCE = 4.5;
    private static final int KEEPS_DISTANCE_TICKS = 60;
    public static final int MAX_CHARGES = 5;
    private static final float ASAL_DAMAGE = 3.0F;
    private static final float ASAL_TRIGGER = 4.0F;
    private static final double ASAL_LENGTH = 5.5;
    private static final double ASAL_WIDTH = 2.0;
    private static final int ASAL_WINDUP = 22;
    public static final int EXHAUSTED_TICKS = 40;
    private static final double DODGE_TRIGGER = 4.0;
    // Right after the target struck: a chance each tick, about one dodge in two strikes when it is free to move.
    private static final int DODGE_WINDOW = 6;
    private static final float DODGE_CHANCE_PER_TICK = 0.12F;

    private static final String COMBO_ID = "forcemaster_combo";
    private static final String JAB_ID = "forcemaster_jab";
    private static final String UPPERCUT_ID = "forcemaster_uppercut";
    private static final String SONIC_ID = "forcemaster_sonic_hand";
    private static final String RUSH_ID = "forcemaster_belial_smashing";
    private static final String NEN_ID = "forcemaster_nen_sphere";
    private static final String ASAL_ID = "forcemaster_asal";
    private static final String DODGE_ID = "forcemaster_dodge";

    // Jabs: a narrow lane straight ahead at chest height; the uppercut reaches a bit higher.
    private static final AttackVolume JAB_LANE = new AttackVolume(AttackShape.THRUST_LANE, SwingPath.lane(0.0F), 3.0, 0.0, 1.4, 2.2, 0.0, 0.0, 1.1);
    private static final AttackVolume UPPERCUT_LANE = new AttackVolume(AttackShape.THRUST_LANE, SwingPath.lane(0.0F), 3.0, 0.0, 1.6, 2.6, 0.0, 0.0, 1.2);
    private static final AttackVolume SONIC_LANE = new AttackVolume(AttackShape.THRUST_LANE, SwingPath.lane(0.0F), 2.9, 0.0, 1.6, 2.2, 0.0, 0.0, 1.1);
    private static final AttackVolume ASAL_LANE = new AttackVolume(AttackShape.THRUST_LANE, SwingPath.lane(0.0F), ASAL_LENGTH, 0.0, ASAL_WIDTH, 2.6, 0.0, 0.0, 1.0);

    private static final DustParticleOptions OVERFLOW = new DustParticleOptions(new Vector3f(0.45F, 0.55F, 1.0F), 0.9F);
    private static final DustParticleOptions OVERFLOW_FULL = new DustParticleOptions(new Vector3f(0.75F, 0.45F, 1.0F), 1.3F);

    // Abilities only for their look (caster_visuals keys); the punches themselves are combat actions.
    private final AbilitySpec sonicLook;
    private final AbilitySpec rushLook;
    private final AbilitySpec asalLook;
    private final AbilitySpec overflowLook;
    private final AbilitySpec nenSphere;
    private final CombatAction jabLeft;
    private final CombatAction jabRight;
    private final CombatAction uppercut;
    private final CombatAction sonicHand;
    private final CombatAction rush;
    private final CombatAction nenCharge;
    private final CombatAction asal;
    private final CombatAction dodge;

    private int charges;
    private long exhaustedUntil;
    private int comboStep;
    private int comboLength;
    private int orbitDirection = 1;
    private int orbitFlipCountdown;
    private int distanceTicks;
    private Vec3 asalForward = Vec3.ZERO;
    // Debug counters (describe).
    private int combos;
    private int combosBroken;
    private int sonicHands;
    private int rushes;
    private int nenSpheres;
    private int nenInterrupted;
    private int asals;
    private int asalHits;
    private int dodges;
    private float lastLift;
    private Vec3 rushStart = Vec3.ZERO;
    private float lastRushDistance;

    public ForcemasterBrain(RenegadeEntity entity) {
        super(entity, Stance.FIGHTING, CombatState.ENGAGING);
        this.sonicLook = look("sonic_hand");
        this.rushLook = look("belial_smashing");
        this.asalLook = look("asal");
        this.overflowLook = look("overflow");
        this.nenSphere = AbilitySpec.builder(id("forcemaster/nen_sphere"), MagicSchool.ARCANE)
                .visual("nen_sphere")
                .delivery(new Delivery.Projectile(Delivery.ProjectileSpec.straight(3.5).withLifetime(4).piercing(4)))
                .impact(new Impact.Damage(NEN_DAMAGE, 0.3F, true))
                .build();

        this.jabLeft = this.jab(JAB_LEFT);
        this.jabRight = this.jab(JAB_RIGHT);
        this.uppercut = CombatAction.builder(UPPERCUT_ID)
                .animation(UPPERCUT)
                .timing(4, 1, 7)
                .motion(AttackMotion.PRESSING)
                .windupDrift(0.3)
                .tracking(TrackingMode.FULL, TrackingMode.LOCKED)
                .cooldown(1)
                .onActiveStart(ctx -> {
                    List<LivingEntity> hit = this.punch(UPPERCUT_LANE, UPPERCUT_DAMAGE, 0.2);
                    for (LivingEntity victim : hit) {
                        this.lift(victim);
                    }
                    this.punchVisual(UPPERCUT_LANE, ParticleTypes.CRIT, 10);
                    this.entity.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.8F);
                })
                .onFinish(ctx -> this.finishCombo(true))
                .build();
        this.sonicHand = CombatAction.builder(SONIC_ID)
                .animation(JAB_LEFT)
                .timing(4, SONIC_HITS * SONIC_INTERVAL, 6)
                .motion(AttackMotion.PLANTED)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(160)
                .onStart(ctx -> this.spellWindup(this.sonicLook))
                .onActiveStart(ctx -> {
                    this.spellRelease(this.sonicLook);
                    this.sonicPunch(0);
                })
                .onActiveTick(ctx -> {
                    int tick = ctx.phaseTick();
                    if (tick % SONIC_INTERVAL == 0 && tick < SONIC_HITS * SONIC_INTERVAL) {
                        this.sonicPunch(tick / SONIC_INTERVAL);
                    }
                })
                .onFinish(ctx -> {
                    this.sonicHands++;
                    this.addCharge();
                })
                .build();
        // The rush's hit zone follows the forcemaster; committed movement stops at walls and at hazards.
        this.rush = CombatAction.builder(RUSH_ID)
                .animation(RUSH)
                .timing(8, 12, 8)
                .motion(AttackMotion.COMMITTED_VECTOR)
                .committed(0.75, 12, 0.0, 0.0)
                .tracking(TrackingMode.FULL, TrackingMode.LOCKED)
                .cooldown(240)
                .volume(AttackVolume.path(3.0, 1.8, 2.4))
                .scaledDamage(RUSH_DAMAGE)
                .knockback(0.0, 0.0)
                .slash(SlashVisual.light(ParticleTypes.CLOUD).withCount(3))
                .onStart(ctx -> {
                    this.spellWindup(this.rushLook);
                    this.entity.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 0.6F);
                })
                .onActiveStart(ctx -> {
                    this.spellRelease(this.rushLook);
                    this.rushStart = this.entity.position();
                })
                .onHit(this::onRushHit)
                .onFinish(ctx -> {
                    this.rushes++;
                    this.lastRushDistance = (float) GroundLocomotion.horizontal(this.entity.position().subtract(this.rushStart)).length();
                    this.addCharge();
                })
                .build();
        this.nenCharge = CombatAction.builder(NEN_ID)
                .animation(NEN_CHARGE)
                .timing(20, 1, 10)
                .motion(AttackMotion.PLANTED)
                .tracking(TrackingMode.FULL, TrackingMode.FULL)
                .cooldown(200)
                .onStart(ctx -> this.spellWindup(this.nenSphere))
                .onActiveStart(ctx -> this.releaseNen())
                .build();
        this.asal = CombatAction.builder(ASAL_ID)
                .animation(ASAL)
                .timing(ASAL_WINDUP, 2, 6)
                .motion(AttackMotion.PLANTED)
                .tracking(TrackingMode.LOCKED, TrackingMode.LOCKED)
                .cooldown(100)
                .onStart(this::announceAsal)
                .onActiveStart(ctx -> this.strikeAsal())
                .onFinish(ctx -> this.becomeExhausted())
                .build();
        this.dodge = CombatAction.builder(DODGE_ID)
                .timing(2, 8, 3)
                .motion(AttackMotion.SIDESTEP)
                .committed(0.5, 8, 0.0, 0.0)
                .tracking(TrackingMode.FULL, TrackingMode.FULL)
                .cooldown(140)
                .onStart(ctx -> {
                    int side = this.entity.getRandom().nextBoolean() ? 1 : -1;
                    ctx.setScratch(side);
                    this.entity.playActionAnimation(RenegadeEntity.ACTION_CONTROLLER, side > 0 ? DODGE_RIGHT : DODGE_LEFT);
                    this.entity.playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.7F, 1.3F);
                    this.dodges++;
                })
                .build();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Renegades.MODID, path);
    }

    private static AbilitySpec look(String key) {
        return AbilitySpec.builder(id("forcemaster/" + key), MagicSchool.ARCANE)
                .visual(key)
                .build();
    }

    private CombatAction jab(String animation) {
        return CombatAction.builder(JAB_ID)
                .animation(animation)
                .timing(3, 1, 3)
                .motion(AttackMotion.PRESSING)
                .windupDrift(0.25)
                .advance(0.1)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(1)
                .onActiveStart(ctx -> {
                    this.punch(JAB_LANE, JAB_DAMAGE, 0.15);
                    this.punchVisual(JAB_LANE, ParticleTypes.CRIT, 4);
                })
                .onFinish(ctx -> this.continueCombo())
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
        return this.jabLeft;
    }

    @Override
    protected void executeCurrentCombatState() {
        LivingEntity target = this.entity.getTarget();
        this.tickOverflow();
        if (this.isExhausted()) {
            this.setStance(Stance.EXHAUSTED);
            this.tickExhausted(target);
            return;
        }
        this.setStance(Stance.FIGHTING);
        if (target == null) {
            return;
        }
        this.tickNenCharge();
        if (this.actionOwnsMovement() || this.handleUnreachableTarget(target) || this.tryChain()) {
            return;
        }

        float distance = this.stimulus.targetDistance;
        this.distanceTicks = distance > KEEPS_DISTANCE ? this.distanceTicks + 1 : 0;
        this.move(target, distance);
        if (this.actions.isBusy() || !this.stimulus.lineOfSightToTarget) {
            return;
        }
        if (this.tryDodge(target, distance)) {
            return;
        }
        if (this.canAsal(target, distance)) {
            this.startFull(this.asal);
        } else if (distance <= SONIC_RANGE && this.cooldowns.isReady(SONIC_ID) && this.canStrike(target, this.jabLeft)) {
            this.startFull(this.sonicHand);
        } else if (distance <= COMBO_RANGE && this.cooldowns.isReady(COMBO_ID) && this.canStrike(target, this.jabLeft)) {
            this.startCombo();
        } else if (distance >= NEN_MIN && distance <= NEN_RANGE && this.cooldowns.isReady(NEN_ID)
                && (distance > RUSH_MAX || this.distanceTicks >= KEEPS_DISTANCE_TICKS || !this.cooldowns.isReady(RUSH_ID))) {
            this.startFull(this.nenCharge);
        } else if (distance >= RUSH_MIN && distance <= RUSH_MAX && this.cooldowns.isReady(RUSH_ID) && this.canStrike(target, this.jabLeft)) {
            this.startFull(this.rush);
        }
    }

    /**
     * Never stands still: closes in fast, circles the target at punching distance (changing direction now and then)
     * and steps in for the next combo.
     */
    private void move(LivingEntity target, float distance) {
        MovementProfile profile = MovementProfile.AGILE_ORBIT;
        this.locomotion.faceTarget(target, profile.turnDegrees());
        if (distance > FLEE_THRESHOLD || !this.stimulus.lineOfSightToTarget) {
            this.locomotion.navigateTo(target.getX(), target.getY(), target.getZ(), 1.2);
            return;
        }
        this.locomotion.setFallbackGoal(target.position(), 1.2);
        if (distance > ORBIT_RADIUS + 1.0 || this.cooldowns.isReady(COMBO_ID)) {
            this.locomotion.pressure(target, PREFERRED_RANGE, MovementProfile.AGILE_BURST);
            return;
        }
        if (--this.orbitFlipCountdown <= 0) {
            this.orbitFlipCountdown = 20 + this.entity.getRandom().nextInt(30);
            this.orbitDirection = -this.orbitDirection;
        }
        this.locomotion.orbit(target, ORBIT_RADIUS, this.orbitDirection, profile);
    }

    /**
     * Techniques keep their full cooldown, unlike the usual random shortening.
     */
    private boolean startFull(CombatAction action) {
        if (this.startAction(action)) {
            this.cooldowns.trigger(action.id(), action.cooldownTicks());
            return true;
        }
        return false;
    }

    private void startCombo() {
        this.comboStep = 0;
        this.comboLength = 3 + this.entity.getRandom().nextInt(2);
        if (this.startAction(this.jabLeft)) {
            this.cooldowns.trigger(COMBO_ID, 60);
        }
    }

    /**
     * After a jab: the next jab (alternating hands), the uppercut as the last strike, or the combo breaks off if the
     * target got out of reach.
     */
    private void continueCombo() {
        this.comboStep++;
        LivingEntity target = this.entity.getTarget();
        if (target == null || !target.isAlive() || this.entity.distanceTo(target) > COMBO_RANGE + 0.6F || !this.entity.hasLineOfSight(target)
                || !withinVerticalReach(this.entity, target, JAB_LANE)) {
            this.finishCombo(false);
            return;
        }
        if (this.comboStep >= this.comboLength - 1) {
            this.chain(this.uppercut);
        } else {
            this.chain(this.comboStep % 2 == 0 ? this.jabLeft : this.jabRight);
        }
    }

    private void finishCombo(boolean complete) {
        this.cooldowns.trigger(COMBO_ID, COMBO_REST + this.entity.getRandom().nextInt(8));
        if (complete) {
            this.combos++;
            this.addCharge();
        } else {
            this.combosBroken++;
        }
    }

    /**
     * Melee hits of the forcemaster's own punches: every target in the lane (line of sight, not an ally) takes attack
     * damage × scale. Fast punches ignore the target's hurt cooldown, like the bolts of the casters.
     */
    private List<LivingEntity> punch(AttackVolume volume, float scale, double knockback) {
        List<LivingEntity> hit = new ArrayList<>();
        if (!(this.entity.level() instanceof ServerLevel level)) {
            return hit;
        }
        AttackFrame frame = AttackGeometry.capture(volume, this.entity, false, 0.0F);
        AABB bounds = AttackGeometry.bounds(volume, frame, null);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, bounds, t -> this.entity.canHarm(t) && this.entity.hasLineOfSight(t))) {
            if (!AttackGeometry.hits(volume, frame, 0.0F, 1.0F, victim.getBoundingBox(), null)) {
                continue;
            }
            victim.invulnerableTime = 0;
            if (this.entity.meleeHit(victim, scale)) {
                hit.add(victim);
                if (knockback > 0.0) {
                    Vec3 push = frame.forward();
                    victim.knockback(knockback, -push.x, -push.z);
                }
            }
        }
        return hit;
    }

    private void punchVisual(AttackVolume volume, net.minecraft.core.particles.ParticleOptions particle, int count) {
        if (this.entity.level() instanceof ServerLevel level) {
            Vec3 at = this.entity.position().add(this.forward().scale(volume.range() * 0.6)).add(0.0, volume.offsetVertical() + 0.2, 0.0);
            level.sendParticles(particle, at.x, at.y, at.z, count, 0.25, 0.25, 0.25, 0.1);
        }
    }

    /**
     * The uppercut lifts the target about a block (velocity, so players are moved by their own client and no flight
     * check triggers). Only a target standing on the ground is lifted, and the upward speed is set, not added, so
     * several forcemasters can never juggle a target into the air. The highest point is recorded for the debug output.
     */
    private void lift(LivingEntity victim) {
        if (!liftFromGround(victim, UPPERCUT_LIFT)) {
            return;
        }
        double startY = victim.getY();
        if (this.entity.level() instanceof ServerLevel level) {
            float[] top = {0.0F};
            for (int delay = 1; delay <= 14; delay++) {
                TickScheduler.schedule(level, delay, () -> {
                    top[0] = Math.max(top[0], (float) (victim.getY() - startY));
                    this.lastLift = top[0];
                });
            }
        }
    }

    private static boolean liftFromGround(LivingEntity victim, double upward) {
        if (!victim.onGround()) {
            return false;
        }
        Vec3 motion = victim.getDeltaMovement();
        victim.setDeltaMovement(motion.x, Math.max(motion.y, upward), motion.z);
        victim.hurtMarked = true;
        return true;
    }

    private void sonicPunch(int index) {
        this.entity.playActionAnimation(RenegadeEntity.ACTION_CONTROLLER, index % 2 == 0 ? JAB_LEFT : JAB_RIGHT);
        List<LivingEntity> hit = this.punch(SONIC_LANE, SONIC_DAMAGE, 0.05);
        this.punchVisual(SONIC_LANE, ParticleTypes.CRIT, 6);
        if (this.entity.level() instanceof ServerLevel level) {
            for (LivingEntity victim : hit) {
                if (index == 0 || index == SONIC_HITS - 1) {
                    AbilityPresentation.spellImpact(level, this.entity, this.sonicLook, victim);
                }
            }
        }
        this.entity.playSound(SoundEvents.PLAYER_ATTACK_WEAK, 0.8F, 1.4F + 0.1F * index);
    }

    /**
     * Rush hit: pushed aside, away from the line the forcemaster runs along.
     */
    private void onRushHit(ActionContext ctx, LivingEntity victim, float dealt) {
        Vec3 forward = ctx.committedForward();
        Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 offset = GroundLocomotion.horizontal(victim.position().subtract(this.entity.position()));
        Vec3 push = lateral.scale(offset.dot(lateral) >= 0.0 ? 1.0 : -1.0);
        victim.knockback(RUSH_SIDE_PUSH, -push.x, -push.z);
        if (this.entity.level() instanceof ServerLevel level) {
            AbilityPresentation.spellImpact(level, this.entity, this.rushLook, victim);
        }
    }

    /**
     * Charge particles gathering at the right fist while the nen sphere charges.
     */
    private void tickNenCharge() {
        if (this.actions.current() != this.nenCharge || this.actions.phase() != AttackPhase.WINDUP
                || !(this.entity.level() instanceof ServerLevel level) || this.actions.phaseTick() % 2 != 0) {
            return;
        }
        Vec3 fist = this.fist(true);
        level.sendParticles(ParticleTypes.ENCHANT, fist.x, fist.y, fist.z, 4, 0.4, 0.4, 0.4, 0.6);
        level.sendParticles(OVERFLOW, fist.x, fist.y, fist.z, 1 + this.actions.phaseTick() / 5, 0.12, 0.12, 0.12, 0.0);
    }

    private void releaseNen() {
        LivingEntity target = this.entity.getTarget();
        if (target == null || !(this.entity.level() instanceof ServerLevel level) || !this.entity.hasLineOfSight(target)) {
            return;
        }
        this.entity.playActionAnimation(RenegadeEntity.ACTION_CONTROLLER, NEN_RELEASE);
        this.entity.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        AbilityExecutor.cast(level, this.entity, this.nenSphere, CastTarget.at(this.entity, target));
        this.nenSpheres++;
        this.addCharge();
    }

    private boolean canAsal(LivingEntity target, float distance) {
        return this.charges >= MAX_CHARGES && RenegadesConfig.ENABLE_ASAL.get() && distance <= ASAL_TRIGGER
                && this.cooldowns.isReady(ASAL_ID) && withinVerticalReach(this.entity, target, ASAL_LANE);
    }

    /**
     * Asal windup: the direction is fixed now and the hit zone in front is marked until the blow, so the target can
     * step out of it.
     */
    private void announceAsal(ActionContext ctx) {
        this.asalForward = ctx.committedForward();
        this.locomotion.snapFacing(this.asalForward);
        this.spellWindup(this.asalLook);
        this.entity.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 1.2F, 1.2F);
        if (this.entity.level() instanceof ServerLevel level) {
            for (int delay = 0; delay < ASAL_WINDUP; delay += 3) {
                TickScheduler.schedule(level, delay, () -> {
                    if (this.entity.isAlive() && this.actions.current() == this.asal && this.actions.phase() == AttackPhase.WINDUP) {
                        this.markAsalZone(level);
                    }
                });
            }
        }
    }

    private void markAsalZone(ServerLevel level) {
        Vec3 forward = this.asalForward;
        Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 base = this.entity.position().add(0.0, 0.15, 0.0);
        double half = ASAL_WIDTH * 0.5;
        for (double along = 0.5; along <= ASAL_LENGTH; along += 0.5) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 at = base.add(forward.scale(along)).add(lateral.scale(half * side));
                level.sendParticles(MagicSchool.ARCANE.particle(), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        for (double across = -half; across <= half; across += 0.5) {
            Vec3 at = base.add(forward.scale(ASAL_LENGTH)).add(lateral.scale(across));
            level.sendParticles(MagicSchool.ARCANE.particle(), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        Vec3 fist = this.fist(true);
        level.sendParticles(OVERFLOW_FULL, fist.x, fist.y, fist.z, 6, 0.25, 0.25, 0.25, 0.0);
    }

    private void strikeAsal() {
        if (!(this.entity.level() instanceof ServerLevel level)) {
            return;
        }
        this.locomotion.snapFacing(this.asalForward);
        this.asals++;
        List<LivingEntity> hit = this.punch(ASAL_LANE, ASAL_DAMAGE, 1.2);
        for (LivingEntity victim : hit) {
            this.asalHits++;
            liftFromGround(victim, 0.2);
            AbilityPresentation.spellImpact(level, this.entity, this.asalLook, victim);
        }
        AbilityPresentation.Handled handled = AbilityPresentation.spellRelease(level, this.entity, this.asalLook, CastTarget.at(this.entity, this.entity.position()));
        Vec3 forward = this.asalForward;
        if (!handled.particles()) {
            for (double along = 1.0; along <= ASAL_LENGTH; along += 1.0) {
                Vec3 at = this.entity.position().add(forward.scale(along)).add(0.0, 1.0, 0.0);
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0.0);
            }
        }
        if (!handled.sounds()) {
            this.entity.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.1F);
        }
        this.charges = 0;
    }

    /**
     * After Asal: exhausted for 2 s, slow and without attacks or dodges (the window for a counterattack).
     */
    private void becomeExhausted() {
        this.exhaustedUntil = this.entity.level().getGameTime() + EXHAUSTED_TICKS;
        this.entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EXHAUSTED_TICKS, 2, false, false), this.entity);
        this.entity.playSound(SoundEvents.RAVAGER_STUNNED, 0.6F, 1.5F);
    }

    public boolean isExhausted() {
        return this.entity.level().getGameTime() < this.exhaustedUntil;
    }

    private void tickExhausted(LivingEntity target) {
        if (target != null) {
            this.locomotion.faceTarget(target, MovementProfile.DISCIPLINED.turnDegrees() * 0.3F);
        }
        this.locomotion.arrest(MovementProfile.DISCIPLINED);
        if (this.entity.tickCount % 5 == 0 && this.entity.level() instanceof ServerLevel level) {
            Vec3 at = this.entity.position();
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 1.9, at.z, 3, 0.25, 0.1, 0.25, 0.01);
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1.7, at.z, 2, 0.3, 0.1, 0.3, 0.0);
        }
    }

    /**
     * A side step when the target strikes at it in melee (a player's attack or a mob's arm swing), not too often.
     */
    private boolean tryDodge(LivingEntity target, float distance) {
        if (distance > DODGE_TRIGGER || !this.cooldowns.isReady(DODGE_ID)) {
            return false;
        }
        boolean strikes = this.stimulus.timeSinceLastPlayerAttack <= DODGE_WINDOW || target.swinging && target.swingTime <= 2;
        if (!strikes || this.entity.getRandom().nextFloat() > DODGE_CHANCE_PER_TICK) {
            return false;
        }
        if (this.startAction(this.dodge)) {
            this.cooldowns.trigger(DODGE_ID, this.dodge.cooldownTicks() + this.entity.getRandom().nextInt(60));
            return true;
        }
        return false;
    }

    /**
     * A charge for a technique or a finished combo (at most 5), with the overflow look on the forcemaster.
     */
    private void addCharge() {
        if (this.charges >= MAX_CHARGES) {
            return;
        }
        this.charges++;
        if (this.entity.level() instanceof ServerLevel level) {
            if (!AbilityPresentation.spellImpact(level, this.entity, this.overflowLook, this.entity).particles()) {
                MagicParticles.pulse(level, MagicSchool.ARCANE, this.entity.getBoundingBox().getCenter(), 0.6F + 0.15F * this.charges);
            }
            if (this.charges == MAX_CHARGES) {
                MagicParticles.sound(level, this.entity.position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.6F);
            }
        }
    }

    /**
     * Arcane particles at both fists, more and bigger with every charge.
     */
    private void tickOverflow() {
        if (this.charges <= 0 || this.entity.tickCount % 4 != 0 || !(this.entity.level() instanceof ServerLevel level)) {
            return;
        }
        DustParticleOptions dust = this.charges >= MAX_CHARGES ? OVERFLOW_FULL : OVERFLOW;
        for (int hand = 0; hand < 2; hand++) {
            Vec3 fist = this.fist(hand == 0);
            level.sendParticles(dust, fist.x, fist.y, fist.z, this.charges, 0.12, 0.12, 0.12, 0.0);
        }
        if (this.charges >= MAX_CHARGES && this.entity.tickCount % 8 == 0) {
            Vec3 fist = this.fist(this.entity.tickCount % 16 == 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, fist.x, fist.y, fist.z, 2, 0.1, 0.1, 0.1, 0.05);
        }
    }

    private Vec3 forward() {
        float yaw = this.entity.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    /**
     * Rough position of a fist in the fighting stance: in front of the chest, to the side of the body.
     */
    private Vec3 fist(boolean right) {
        Vec3 forward = this.forward();
        Vec3 rightSide = new Vec3(-forward.z, 0.0, forward.x);
        return this.entity.position().add(forward.scale(0.35)).add(rightSide.scale(right ? 0.38 : -0.38)).add(0.0, 1.05, 0.0);
    }

    private void spellWindup(AbilitySpec spec) {
        if (this.entity.level() instanceof ServerLevel level) {
            AbilityPresentation.spellWindup(level, this.entity, spec);
        }
    }

    private void spellRelease(AbilitySpec spec) {
        if (this.entity.level() instanceof ServerLevel level) {
            AbilityPresentation.spellRelease(level, this.entity, spec, CastTarget.at(this.entity, this.entity.position()));
        }
    }

    /**
     * A hit of 4+ damage while the nen sphere charges stops it; its cooldown then runs at half length.
     */
    @Override
    public void onDamageTaken(DamageSource source, float amount) {
        super.onDamageTaken(source, amount);
        if (this.actions.current() != this.nenCharge || this.actions.phase() != AttackPhase.WINDUP || amount < NEN_INTERRUPT) {
            return;
        }
        this.actions.cancel();
        this.cooldowns.trigger(NEN_ID, this.nenCharge.cooldownTicks() / 2);
        this.nenInterrupted++;
        if (this.entity.level() instanceof ServerLevel level) {
            Vec3 fist = this.fist(true);
            level.sendParticles(ParticleTypes.SMOKE, fist.x, fist.y, fist.z, 10, 0.2, 0.2, 0.2, 0.02);
            MagicParticles.sound(level, fist, SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.4F);
        }
    }

    @Override
    public void onTargetLost() {
        super.onTargetLost();
        this.charges = 0;
        this.distanceTicks = 0;
    }

    public int charges() {
        return this.charges;
    }

    @Override
    public String describe() {
        long exhausted = Math.max(0L, this.exhaustedUntil - this.entity.level().getGameTime());
        return super.describe() + String.format(Locale.ROOT,
                ", charges %d/%d, exhausted %d, combos %d (broken %d), sonic %d, rush %d (last %.1f blocks), nen %d (interrupted %d), asal %d (hits %d), dodges %d, last lift %.2f, cooldowns combo %d / sonic %d / rush %d / nen %d / dodge %d",
                this.charges, MAX_CHARGES, exhausted, this.combos, this.combosBroken, this.sonicHands, this.rushes, this.lastRushDistance, this.nenSpheres, this.nenInterrupted, this.asals,
                this.asalHits, this.dodges, this.lastLift, this.cooldowns.remaining(COMBO_ID), this.cooldowns.remaining(SONIC_ID),
                this.cooldowns.remaining(RUSH_ID), this.cooldowns.remaining(NEN_ID), this.cooldowns.remaining(DODGE_ID));
    }

    public enum CombatState implements BrainState {
        ENGAGING;

        @Override
        public String id() {
            return this.name();
        }
    }

    public enum Stance implements BrainState {
        FIGHTING,
        EXHAUSTED;

        @Override
        public String id() {
            return this.name();
        }
    }
}
