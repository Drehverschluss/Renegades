package com.drehverschluss.renegades.combat.brain;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.combat.AttackMotion;
import com.drehverschluss.renegades.combat.AttackPhase;
import com.drehverschluss.renegades.combat.CombatAction;
import com.drehverschluss.renegades.combat.GroundLocomotion;
import com.drehverschluss.renegades.combat.MovementProfile;
import com.drehverschluss.renegades.combat.TrackingMode;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.PriestRenegade;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.drehverschluss.renegades.magic.AbilityExecutor;
import com.drehverschluss.renegades.magic.AbilitySpec;
import com.drehverschluss.renegades.magic.AreaShape;
import com.drehverschluss.renegades.magic.CastTarget;
import com.drehverschluss.renegades.magic.Delivery;
import com.drehverschluss.renegades.magic.Impact;
import com.drehverschluss.renegades.magic.ImpactContext;
import com.drehverschluss.renegades.magic.MagicBackends;
import com.drehverschluss.renegades.magic.MagicParticles;
import com.drehverschluss.renegades.magic.MagicSchool;
import com.drehverschluss.renegades.magic.MagicSounds;
import com.drehverschluss.renegades.magic.Relations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Priest combat (own design from the magic and combat building blocks; ANARCHY has no healer brain).
 * <p>
 * Stance SUPPORTING while allies (group members or other renegades) are within 16 blocks, ALONE otherwise. Combat state
 * POSITIONING keeps 8-14 blocks from the target, if possible behind an ally (the ally between priest and target);
 * EVADING when the target comes closer than 4 blocks: jump back or back off, no long casts.
 * <p>
 * Actions, by priority: self heal (alone, below 40 %), healing cloud (2+ allies close together below 60 %), single heal
 * (the ally with the lowest health below 70 %), protection (absorption on an ally that was just hit) and, only while
 * nobody needs healing or when alone, a holy bolt. The single heal, the self heal and the cloud have a visible windup
 * and stand still; a hit of 4+ damage or any melee hit during the windup interrupts them (half the cooldown).
 */
public class PriestBrain extends MobBrain {
    private static final String MAGE = "animation.v2.mage.";
    public static final String HEAL = "animation.templar_heal";
    public static final String CAST_QUICK = MAGE + "cast_quick";
    public static final String CAST_LONG = MAGE + "cast_long";
    public static final String JUMP_BACK = MAGE + "jump_back";
    /**
     * All triggered animations of this brain; HEAL is a loop, the others play once.
     */
    public static final List<String> ACTION_ANIMATIONS = List.of(HEAL, CAST_QUICK, CAST_LONG, JUMP_BACK);

    private static final float MELEE_THRESHOLD = 4.0F;
    private static final float ENGAGEMENT_THRESHOLD = 20.0F;
    private static final float FLEE_THRESHOLD = 24.0F;
    private static final float DAMAGE_EXCEEDED_THRESHOLD = 8.0F;

    private static final double SUPPORT_RANGE = 16.0;
    private static final double BAND_NEAR = 8.0;
    private static final double BAND_FAR = 14.0;
    private static final double EVADE_RANGE = 4.0;
    private static final double EVADE_EXIT = 6.0;
    // How far behind the anchor ally (seen from the target) the priest wants to stand.
    private static final double BEHIND_ALLY = 4.0;
    private static final double MAX_CAST_RANGE = 24.0;

    private static final float HEAL_BELOW = 0.7F;
    private static final float HEAL_FACTOR = 2.5F;
    private static final float HEAL_CAP = 0.25F;
    private static final float CLOUD_BELOW = 0.6F;
    private static final double CLOUD_CLUSTER = 8.0;
    private static final double CLOUD_RADIUS = 4.0;
    private static final int CLOUD_DURATION = 100;
    private static final int CLOUD_INTERVAL = 20;
    private static final float CLOUD_FACTOR = 0.8F;
    // Five pulses of at most 6 % each: at most 30 % per target and cloud.
    private static final float CLOUD_CAP_PER_PULSE = 0.06F;
    private static final float SELF_HEAL_BELOW = 0.4F;
    private static final int PROTECT_TICKS = 200;
    private static final int RECENTLY_HIT_TICKS = 40;
    private static final float BOLT_DAMAGE = 0.35F;
    private static final float INTERRUPT_DAMAGE = 4.0F;

    private static final String HEAL_ID = "priest_heal";
    private static final String SELF_HEAL_ID = "priest_self_heal";
    private static final String CLOUD_ID = "priest_heal_cloud";
    private static final String PROTECT_ID = "priest_protect";
    private static final String BOLT_ID = "priest_holy_bolt";
    private static final String JUMP_BACK_ID = "priest_jump_back";

    private final AbilitySpec healSpell;
    private final AbilitySpec cloudSpell;
    private final AbilitySpec boltSpell;
    private final CombatAction healCast;
    private final CombatAction selfHealCast;
    private final CombatAction cloudCast;
    private final CombatAction protectCast;
    private final CombatAction boltCast;
    private final CombatAction jumpBack;

    private List<RenegadeEntity> allies = List.of();
    private int allyScanCountdown;
    @Nullable
    private LivingEntity healTarget;
    @Nullable
    private Vec3 cloudCentre;
    @Nullable
    private LivingEntity protectTarget;
    @Nullable
    private Vec3 positionGoal;
    private int goalCountdown;
    private int interruptions;
    private double healedTotal;

    public PriestBrain(PriestRenegade entity) {
        super(entity, Stance.ALONE, CombatState.POSITIONING);
        this.healSpell = AbilitySpec.builder(id("priest/heal"), MagicSchool.HEALING)
                .helpful()
                .impact(new Impact.Heal(HEAL_FACTOR, HEAL_CAP))
                .build();
        this.cloudSpell = AbilitySpec.builder(id("priest/heal_cloud"), MagicSchool.HEALING)
                .helpful()
                .delivery(new Delivery.Cloud(new Delivery.CloudSpec(AreaShape.circle(CLOUD_RADIUS), 3.0, CLOUD_DURATION, CLOUD_INTERVAL), Delivery.Anchor.TARGET))
                .impact(new Impact.Heal(CLOUD_FACTOR, CLOUD_CAP_PER_PULSE))
                .build();
        this.boltSpell = AbilitySpec.builder(id("priest/holy_bolt"), MagicSchool.HEALING)
                .delivery(new Delivery.Projectile(Delivery.ProjectileSpec.straight(1.4).withLifetime(25)))
                .impact(new Impact.Damage(BOLT_DAMAGE, 0.3F, true))
                .build();

        this.healCast = this.healAction(HEAL_ID, 160, false);
        this.selfHealCast = this.healAction(SELF_HEAL_ID, 400, true);
        this.cloudCast = CombatAction.builder(CLOUD_ID)
                .animation(CAST_LONG)
                .timing(54, 6, 14)
                .motion(AttackMotion.PLANTED)
                .tracking(TrackingMode.LOCKED, TrackingMode.LOCKED)
                .cooldown(500)
                .onStart(ctx -> {
                    if (this.entity.level() instanceof ServerLevel level && this.cloudCentre != null) {
                        AbilityExecutor.windup(level, this.entity, this.cloudSpell, CastTarget.at(this.entity, this.cloudCentre));
                    }
                })
                .onActiveStart(ctx -> {
                    if (this.entity.level() instanceof ServerLevel level && this.cloudCentre != null) {
                        AbilityExecutor.cast(level, this.entity, this.cloudSpell, CastTarget.at(this.entity, this.cloudCentre));
                    }
                })
                .build();
        this.protectCast = CombatAction.builder(PROTECT_ID)
                .animation(CAST_QUICK)
                .timing(4, 2, 6)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.LOCKED, TrackingMode.LOCKED)
                .cooldown(400)
                .onActiveStart(ctx -> this.releaseProtection())
                .build();
        this.boltCast = CombatAction.builder(BOLT_ID)
                .animation(CAST_QUICK)
                .timing(4, 4, 6)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(80)
                .onActiveStart(ctx -> this.releaseBolt())
                .build();
        this.jumpBack = CombatAction.builder(JUMP_BACK_ID)
                .animation(JUMP_BACK)
                .timing(4, 12, 3)
                .motion(AttackMotion.RETREATING)
                .committed(0.42, 12, 0.46, 0.0)
                .tracking(TrackingMode.FULL, TrackingMode.MINIMAL)
                .cooldown(120)
                .onStart(ctx -> ctx.entity().playSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 1.3F))
                .build();
    }

    /**
     * Heal with a windup of 1.5 s: the priest stands still with raised hands, healing particles around it.
     */
    private CombatAction healAction(String actionId, int cooldown, boolean self) {
        return CombatAction.builder(actionId)
                .animation(HEAL)
                .timing(30, 1, 8)
                .motion(AttackMotion.PLANTED)
                .tracking(TrackingMode.LOCKED, TrackingMode.LOCKED)
                .cooldown(cooldown)
                .onStart(ctx -> {
                    if (this.entity.level() instanceof ServerLevel level) {
                        MagicParticles.sound(level, this.entity.position(), MagicSounds.HEALING.windup(), 1.5F, 1.0F);
                    }
                })
                .onActiveStart(ctx -> this.releaseHeal(self ? this.entity : this.healTarget))
                .onRecoveryStart(ctx -> this.entity.stopActionAnimation(RenegadeEntity.ACTION_CONTROLLER, HEAL))
                .build();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Renegades.MODID, path);
    }

    @Override
    protected AIStimulus createStimulus() {
        return new AIStimulus(this.entity, MELEE_THRESHOLD, ENGAGEMENT_THRESHOLD, FLEE_THRESHOLD, DAMAGE_EXCEEDED_THRESHOLD);
    }

    @Override
    protected List<Transition> stanceTransitions() {
        return List.of(
                new Transition(Stance.ALONE, s -> !this.allies.isEmpty(), Stance.SUPPORTING, 1),
                new Transition(Stance.SUPPORTING, s -> this.allies.isEmpty(), Stance.ALONE, 1)
        );
    }

    @Override
    protected List<Transition> combatStateTransitions() {
        return List.of(
                new Transition(CombatState.POSITIONING, s -> s.targetDistance < EVADE_RANGE, CombatState.EVADING, 10),
                new Transition(CombatState.EVADING, s -> s.targetDistance >= EVADE_EXIT, CombatState.POSITIONING, 10)
        );
    }

    @Override
    protected List<PhaseTransition> definePhaseTransitions() {
        return List.of();
    }

    @Override
    protected void executeCurrentCombatState() {
        LivingEntity target = this.entity.getTarget();
        if (target == null) {
            return;
        }
        if (--this.allyScanCountdown <= 0) {
            this.allyScanCountdown = 5;
            this.allies = this.scanAllies();
        }
        this.tickRunningCast();
        if (this.actionOwnsMovement() || this.tryChain()) {
            return;
        }
        if (!this.actions.isBusy()) {
            this.chooseAction(target);
            if (this.actionOwnsMovement()) {
                return;
            }
        }
        this.move(target);
    }

    /**
     * While a heal or the cloud winds up: face the ally (or the cloud's spot), show healing particles, and give up if
     * the ally died or went out of reach.
     */
    private void tickRunningCast() {
        CombatAction current = this.actions.current();
        if (current == null || !(this.entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (current == this.healCast && this.healTarget != null) {
            if (!this.healTarget.isAlive() || this.entity.distanceTo(this.healTarget) > SUPPORT_RANGE + 2.0) {
                this.actions.cancel();
                return;
            }
            this.locomotion.facePosition(this.healTarget.getX(), this.healTarget.getZ(), 20.0F);
        } else if (current == this.cloudCast && this.cloudCentre != null) {
            this.locomotion.facePosition(this.cloudCentre.x, this.cloudCentre.z, 20.0F);
        } else if (current == this.protectCast && this.protectTarget != null) {
            this.locomotion.facePosition(this.protectTarget.getX(), this.protectTarget.getZ(), 30.0F);
        }
        boolean healing = current == this.healCast || current == this.selfHealCast || current == this.cloudCast;
        if (healing && this.actions.phase() == AttackPhase.WINDUP && this.actions.phaseTick() % 4 == 0) {
            Vec3 at = this.entity.position();
            level.sendParticles(MagicSchool.HEALING.particle(), at.x, at.y + 1.0, at.z, 3, 0.45, 0.6, 0.45, 0.0);
            level.sendParticles(MagicSchool.HEALING.dust(1.0F), at.x, at.y + 1.6, at.z, 4, 0.35, 0.35, 0.35, 0.0);
        }
    }

    private void chooseAction(LivingEntity target) {
        boolean healingOn = RenegadesConfig.ENABLE_PRIEST_HEALING.get();
        boolean evading = this.currentCombatState == CombatState.EVADING;
        boolean alone = this.allies.isEmpty();

        if (evading && this.cooldowns.isReady(JUMP_BACK_ID) && this.startAction(this.jumpBack)) {
            return;
        }
        if (healingOn && !evading) {
            if (alone && this.entity.getHealth() < this.entity.getMaxHealth() * SELF_HEAL_BELOW && this.cooldowns.isReady(SELF_HEAL_ID)) {
                this.startSupport(this.selfHealCast);
                return;
            }
            if (this.cooldowns.isReady(CLOUD_ID)) {
                Vec3 centre = this.findCloudCentre();
                if (centre != null) {
                    this.cloudCentre = centre;
                    this.startSupport(this.cloudCast);
                    return;
                }
            }
            if (this.cooldowns.isReady(HEAL_ID)) {
                LivingEntity wounded = this.mostWounded();
                if (wounded != null) {
                    this.healTarget = wounded;
                    this.startSupport(this.healCast);
                    return;
                }
            }
        }
        if (this.cooldowns.isReady(PROTECT_ID)) {
            LivingEntity attacked = this.attackedAlly();
            if (attacked != null) {
                this.protectTarget = attacked;
                this.startSupport(this.protectCast);
                return;
            }
        }
        boolean someoneNeedsHealing = healingOn && !alone && this.mostWounded() != null;
        if (!someoneNeedsHealing && this.cooldowns.isReady(BOLT_ID) && this.stimulus.lineOfSightToTarget
                && this.stimulus.targetDistance <= MAX_CAST_RANGE) {
            this.startAction(this.boltCast);
        }
    }

    /**
     * Support spells keep their full cooldown: the usual shortening (see RenegadeEntity#scaledCooldown, about 40 %
     * alone and more in groups) would let a priest heal almost without pause.
     */
    private void startSupport(CombatAction action) {
        if (this.startAction(action)) {
            this.cooldowns.trigger(action.id(), action.cooldownTicks());
        }
    }

    /**
     * Keeps 8-14 blocks from the target, preferably behind an ally; backs off when the target is too close.
     */
    private void move(LivingEntity target) {
        MovementProfile profile = MovementProfile.ELASTIC;
        if (this.actions.current() != this.protectCast) {
            this.locomotion.faceTarget(target, profile.turnDegrees());
        }
        double distance = GroundLocomotion.horizontal(target.position().subtract(this.entity.position())).length();
        if (this.currentCombatState == CombatState.EVADING || distance < BAND_NEAR) {
            this.locomotion.setFallbackGoal(this.entity.position().add(this.locomotion.retreatVector(target).scale(8.0)), 1.3);
            this.locomotion.retreatFrom(target, BAND_NEAR, profile.withMaxSpeed(profile.maxSpeed() * 1.2));
            return;
        }
        if (!this.stimulus.lineOfSightToTarget && this.allies.isEmpty()) {
            this.locomotion.navigateTo(target.getX(), target.getY(), target.getZ(), 1.0);
            return;
        }
        if (--this.goalCountdown <= 0 || this.positionGoal == null) {
            this.goalCountdown = 10;
            this.positionGoal = this.choosePosition(target, distance);
        }
        Vec3 goal = this.positionGoal;
        if (goal == null || GroundLocomotion.horizontal(goal.subtract(this.entity.position())).length() < 1.2) {
            this.locomotion.arrest(profile);
            return;
        }
        this.locomotion.seekPosition(goal, profile);
    }

    /**
     * Behind the ally fighting closest to the target (seen from the target), 9-13 blocks from the target; without an
     * ally, null inside the 8-14 band (stay) or the nearest point of the band.
     */
    @Nullable
    private Vec3 choosePosition(LivingEntity target, double distance) {
        RenegadeEntity anchor = this.allies.stream()
                .filter(ally -> ally.distanceTo(target) < 10.0F)
                .min(Comparator.comparingDouble(ally -> ally.distanceToSqr(target)))
                .orElse(null);
        if (anchor != null) {
            Vec3 away = GroundLocomotion.horizontal(anchor.position().subtract(target.position()));
            // An ally right at the target gives no stable direction; then the priest keeps its own bearing.
            if (away.lengthSqr() > 2.5 * 2.5) {
                double range = Mth.clamp(away.length() + BEHIND_ALLY, 9.0, 13.0);
                return target.position().add(away.normalize().scale(range));
            }
        }
        if (distance <= BAND_FAR) {
            return null;
        }
        Vec3 away = GroundLocomotion.horizontal(this.entity.position().subtract(target.position()));
        return away.lengthSqr() < 1.0E-4 ? null : target.position().add(away.normalize().scale(BAND_FAR - 1.0));
    }

    private List<RenegadeEntity> scanAllies() {
        return this.entity.level().getEntitiesOfClass(RenegadeEntity.class, this.entity.getBoundingBox().inflate(SUPPORT_RANGE),
                other -> other != this.entity && other.distanceTo(this.entity) <= SUPPORT_RANGE && Relations.canHelp(this.entity, other));
    }

    private boolean canReach(LivingEntity ally) {
        return ally.isAlive() && this.entity.distanceTo(ally) <= SUPPORT_RANGE && this.entity.hasLineOfSight(ally);
    }

    private static float healthPct(LivingEntity entity) {
        return entity.getHealth() / Math.max(1.0F, entity.getMaxHealth());
    }

    /**
     * The ally in reach with the lowest health in percent, if below 70 %.
     */
    @Nullable
    private LivingEntity mostWounded() {
        return this.allies.stream()
                .filter(ally -> healthPct(ally) < HEAL_BELOW && this.canReach(ally))
                .min(Comparator.comparingDouble(PriestBrain::healthPct))
                .orElse(null);
    }

    /**
     * Where a healing cloud would reach at least two allies below 60 % within 8 blocks of each other: the middle of the
     * largest such cluster.
     */
    @Nullable
    private Vec3 findCloudCentre() {
        List<RenegadeEntity> wounded = new ArrayList<>();
        for (RenegadeEntity ally : this.allies) {
            if (healthPct(ally) < CLOUD_BELOW && this.canReach(ally)) {
                wounded.add(ally);
            }
        }
        if (this.entity.getHealth() < this.entity.getMaxHealth() * CLOUD_BELOW) {
            wounded.add(this.entity);
        }
        List<LivingEntity> best = List.of();
        for (LivingEntity centre : wounded) {
            List<LivingEntity> cluster = wounded.stream().filter(other -> other.distanceTo(centre) <= CLOUD_CLUSTER).map(other -> (LivingEntity) other).toList();
            if (cluster.size() > best.size()) {
                best = cluster;
            }
        }
        if (best.size() < 2 || best.stream().allMatch(member -> member == this.entity)) {
            return null;
        }
        double x = 0.0;
        double z = 0.0;
        double y = Double.MAX_VALUE;
        for (LivingEntity member : best) {
            x += member.getX();
            z += member.getZ();
            y = Math.min(y, member.getY());
        }
        return new Vec3(x / best.size(), y, z / best.size());
    }

    /**
     * An ally in reach that was hit within the last two seconds and has no absorption yet.
     */
    @Nullable
    private LivingEntity attackedAlly() {
        return this.allies.stream()
                .filter(ally -> ally.getLastHurtByMob() != null && ally.tickCount - ally.getLastHurtByMobTimestamp() < RECENTLY_HIT_TICKS
                        && !ally.hasEffect(MobEffects.ABSORPTION) && this.canReach(ally))
                .min(Comparator.comparingDouble(PriestBrain::healthPct))
                .orElse(null);
    }

    private void releaseHeal(@Nullable LivingEntity ally) {
        if (!(this.entity.level() instanceof ServerLevel level) || ally == null) {
            return;
        }
        boolean self = ally == this.entity;
        if (!self && (!this.canReach(ally) || !Relations.canHelp(this.entity, ally))) {
            this.fizzle(level);
            return;
        }
        if (!self) {
            this.beam(level, ally, MagicSchool.HEALING.dust(1.2F));
        }
        AbilityExecutor.applyAll(this.healSpell, ImpactContext.of(this.entity, MagicSchool.HEALING, this.entity.position(), null), ally);
        Vec3 centre = ally.getBoundingBox().getCenter();
        MagicParticles.pulse(level, MagicSchool.HEALING, centre, 1.4F);
        level.sendParticles(ParticleTypes.HEART, centre.x, ally.getY() + ally.getBbHeight() + 0.3, centre.z, 3, 0.3, 0.2, 0.3, 0.0);
        MagicParticles.sound(level, centre, MagicSounds.HEALING.release(), 1.2F, 1.2F);
    }

    private void releaseProtection() {
        LivingEntity ally = this.protectTarget;
        if (!(this.entity.level() instanceof ServerLevel level) || ally == null || !this.canReach(ally)) {
            return;
        }
        this.beam(level, ally, MagicSchool.LIGHTNING.dust(1.0F));
        ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, PROTECT_TICKS, 0), this.entity);
        Vec3 centre = ally.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y, centre.z, 12, 0.4, 0.6, 0.4, 0.02);
        MagicParticles.sound(level, centre, SoundEvents.ARMOR_EQUIP_GOLD.value(), 1.2F, 1.0F);
    }

    private void releaseBolt() {
        LivingEntity target = this.entity.getTarget();
        if (target != null && target.isAlive() && this.entity.level() instanceof ServerLevel level && this.entity.hasLineOfSight(target)) {
            this.entity.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            AbilityExecutor.cast(level, this.entity, this.boltSpell, CastTarget.at(this.entity, target));
        }
    }

    /**
     * A line of particles from the priest's hands to the ally.
     */
    private void beam(ServerLevel level, LivingEntity ally, net.minecraft.core.particles.ParticleOptions particle) {
        Vec3 from = this.entity.position().add(0.0, this.entity.getBbHeight() * 0.7, 0.0);
        Vec3 to = ally.getBoundingBox().getCenter();
        Vec3 step = to.subtract(from);
        int points = Math.max(2, (int) (step.length() / 0.4));
        for (int i = 0; i <= points; i++) {
            Vec3 at = from.add(step.scale(i / (double) points));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void fizzle(ServerLevel level) {
        Vec3 at = this.entity.position();
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 1.4, at.z, 10, 0.3, 0.3, 0.3, 0.02);
        MagicParticles.sound(level, at, SoundEvents.FIRE_EXTINGUISH, 0.8F, 1.4F);
    }

    /**
     * A hit of 4+ damage or any melee hit during the windup of a heal, a self heal or the cloud breaks the cast; its
     * cooldown then runs at half length.
     */
    @Override
    public void onDamageTaken(DamageSource source, float amount) {
        super.onDamageTaken(source, amount);
        CombatAction current = this.actions.current();
        boolean interruptible = current == this.healCast || current == this.selfHealCast || current == this.cloudCast;
        if (!interruptible || this.actions.phase() != AttackPhase.WINDUP) {
            return;
        }
        boolean melee = source.getEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()
                && !source.is(DamageTypeTags.IS_PROJECTILE);
        if (amount < INTERRUPT_DAMAGE && !melee) {
            return;
        }
        this.actions.cancel();
        this.cooldowns.trigger(current.id(), current.cooldownTicks() / 2);
        this.interruptions++;
        if (this.entity.level() instanceof ServerLevel level) {
            this.fizzle(level);
        }
    }

    @Override
    public void onTargetLost() {
        super.onTargetLost();
        this.positionGoal = null;
        this.healTarget = null;
        this.protectTarget = null;
        this.cloudCentre = null;
    }

    public void recordHeal(float amount) {
        this.healedTotal += amount;
    }

    @Override
    public String describe() {
        CombatAction current = this.actions.current();
        String doing;
        if (current == this.healCast && this.healTarget != null) {
            doing = String.format(Locale.ROOT, "healing %s (%.1f/%.1f)", this.healTarget.getName().getString(), this.healTarget.getHealth(),
                    this.healTarget.getMaxHealth());
        } else if (current == this.selfHealCast) {
            doing = "healing itself";
        } else if (current == this.cloudCast) {
            doing = "casting healing cloud";
        } else if (current == this.protectCast && this.protectTarget != null) {
            doing = "protecting " + this.protectTarget.getName().getString();
        } else if (current == this.boltCast) {
            doing = "attacking";
        } else if (current == this.jumpBack) {
            doing = "jumping back";
        } else {
            doing = "positioning";
        }
        return super.describe() + String.format(Locale.ROOT,
                ", target %.1f blocks, %s, allies %d, cooldowns heal %d / cloud %d / protect %d / bolt %d / self %d, interrupted %d, healed %.1f HP, backend %s, heal power %.1f",
                this.stimulus.targetDistance, doing, this.allies.size(), this.cooldowns.remaining(HEAL_ID), this.cooldowns.remaining(CLOUD_ID), this.cooldowns.remaining(PROTECT_ID),
                this.cooldowns.remaining(BOLT_ID), this.cooldowns.remaining(SELF_HEAL_ID), this.interruptions, this.healedTotal,
                MagicBackends.active().name(), MagicBackends.basePower(this.entity, MagicSchool.HEALING) * HEAL_FACTOR
                        * RenegadesConfig.HEALING_MULTIPLIER.get());
    }

    public enum Stance implements BrainState {
        SUPPORTING,
        ALONE;

        @Override
        public String id() {
            return this.name();
        }
    }

    public enum CombatState implements BrainState {
        POSITIONING,
        EVADING;

        @Override
        public String id() {
            return this.name();
        }
    }
}
