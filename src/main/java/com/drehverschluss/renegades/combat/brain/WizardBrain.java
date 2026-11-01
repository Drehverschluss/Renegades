// Based on FireMageBrain from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: one brain for the fire, frost and arcane wizard (school and extra effect from the variant); spells through
// our magic package; nova can be switched off; no talking animation, ribbons or AtlasVFX cones; other numbers
// unchanged.
package com.drehverschluss.renegades.combat.brain;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.combat.ActionContext;
import com.drehverschluss.renegades.combat.AttackMotion;
import com.drehverschluss.renegades.combat.CombatAction;
import com.drehverschluss.renegades.combat.MovementProfile;
import com.drehverschluss.renegades.combat.SlashVisual;
import com.drehverschluss.renegades.combat.TrackingMode;
import com.drehverschluss.renegades.combat.geometry.AttackVolume;
import com.drehverschluss.renegades.combat.geometry.SwingDynamics;
import com.drehverschluss.renegades.combat.geometry.SwingPath;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.drehverschluss.renegades.entity.WizardRenegade;
import com.drehverschluss.renegades.entity.WizardVariant;
import com.drehverschluss.renegades.magic.AbilityExecutor;
import com.drehverschluss.renegades.magic.AbilityPresentation;
import com.drehverschluss.renegades.magic.AbilitySpec;
import com.drehverschluss.renegades.magic.AreaShape;
import com.drehverschluss.renegades.magic.CastTarget;
import com.drehverschluss.renegades.magic.Delivery;
import com.drehverschluss.renegades.magic.Impact;
import com.drehverschluss.renegades.magic.MagicBackends;
import com.drehverschluss.renegades.magic.MagicParticles;
import com.drehverschluss.renegades.magic.MagicSchool;
import com.drehverschluss.renegades.magic.TickScheduler;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Wizard combat: circles its target at 6-9 blocks and throws spell bolts (two per cast); a stationary target gets a
 * volley of four homing bolts that burst; a target that comes too close is hit by a nova around the wizard, followed
 * by a jump back, or by a short flame sweep. Fire spells set targets on fire, frost spells slow them, arcane spells fly
 * faster.
 */
public class WizardBrain extends MobBrain {
    private static final String FAMILY = "animation.v2.mage.";
    public static final String CAST_QUICK = FAMILY + "cast_quick";
    public static final String CAST_LONG = FAMILY + "cast_long";
    public static final String NOVA = FAMILY + "nova";
    public static final String JUMP_BACK = FAMILY + "jump_back";
    public static final String FLAME_SWEEP = FAMILY + "flame_sweep";
    /**
     * All triggered animations of this brain; registered on the renegade's action controller on both sides.
     */
    public static final List<String> ACTION_ANIMATIONS = List.of(CAST_QUICK, CAST_LONG, NOVA, JUMP_BACK, FLAME_SWEEP);

    private static final float MELEE_THRESHOLD = 4.0F;
    private static final float ENGAGEMENT_THRESHOLD = 20.0F;
    private static final float FLEE_THRESHOLD = 24.0F;
    private static final float DAMAGE_EXCEEDED_THRESHOLD = 10.0F;
    private static final double ORBIT_RADIUS = 7.5;
    private static final double BREACH_RANGE = 4.5;
    private static final double SPACING_NEAR = 6.0;
    // Longest distance the wizard casts from (the fireball flies 32 blocks).
    private static final float MAX_CAST_RANGE = 24.0F;
    // fire_mage/fireball: damage 0.4 x power, knockback 0.2, speed 1.6, flies 20 ticks.
    private static final float FIREBALL_DAMAGE = 0.4F;
    // fire_mage/volley_bolt: damage 0.2 x power, knockback 2, homing 20 degrees per tick from tick 4 to 12, burst radius 4.
    private static final float[] VOLLEY_YAW_OFFSETS = {45.0F, -45.0F, 135.0F, -135.0F};
    // Frost: slowness II for 2 s (bolts), 3 s (sweep) or 4 s (nova).
    private static final int FROST_AMPLIFIER = 1;

    private static final String FIREBALL_ID = "wizard_fireball";
    private static final String VOLLEY_ID = "wizard_volley";
    private static final String NOVA_ID = "wizard_nova";
    private static final String JUMP_BACK_ID = "wizard_jump_back";
    private static final String SWEEP_ID = "wizard_flame_sweep";

    private final WizardRenegade wizard;
    private final Map<WizardVariant, Spells> spellsByVariant = new EnumMap<>(WizardVariant.class);
    private final CombatAction jumpBack;
    private int targetStationaryTicks;
    @Nullable
    private Vec3 prevTargetPos;
    private int orbitDirection = 1;
    private int orbitFlipTimer;

    /**
     * Spells and actions of one variant.
     */
    private record Spells(AbilitySpec fireball, AbilitySpec volleyBolt, AbilitySpec nova, CombatAction fireballCast, CombatAction volleyCast,
                          CombatAction novaCast, CombatAction flameSweep) {
    }

    public WizardBrain(WizardRenegade entity) {
        super(entity, Stance.HARASSMENT, CombatState.PRESSURING);
        this.wizard = entity;
        this.jumpBack = CombatAction.builder(JUMP_BACK_ID)
                .animation(JUMP_BACK)
                .timing(4, 12, 3)
                .motion(AttackMotion.RETREATING)
                .committed(0.42, 12, 0.46, 0.0)
                .tracking(TrackingMode.FULL, TrackingMode.MINIMAL)
                .cooldown(400)
                .onStart(ctx -> ctx.entity().playSound(net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 0.7F, 1.4F))
                .onFinish(ctx -> {
                    LivingEntity target = ctx.liveTarget();
                    if (target != null) {
                        ctx.locomotion().faceTarget(target, 90.0F);
                    }
                    if (this.cooldowns.isReady(FIREBALL_ID)) {
                        this.chain(this.spells().fireballCast());
                    }
                })
                .build();
    }

    private Spells spells() {
        return this.spellsByVariant.computeIfAbsent(this.wizard.getVariant(), this::createSpells);
    }

    private Spells createSpells(WizardVariant variant) {
        MagicSchool school = variant.school();
        String prefix = "wizard/" + variant.id() + "/";
        AbilitySpec fireball = this.withVariantEffect(AbilitySpec.builder(id(prefix + "fireball"), school)
                .visual("double_bolt")
                .delivery(new Delivery.Projectile(Delivery.ProjectileSpec.straight(1.6 * variant.projectileSpeedFactor()).withLifetime(20)))
                .impact(new Impact.Damage(FIREBALL_DAMAGE, 0.2F, true)), variant, 1.5F, 40).build();
        AbilitySpec volleyBolt = AbilitySpec.builder(id(prefix + "volley_bolt"), school)
                .visual("homing_bolts")
                .delivery(new Delivery.Projectile(Delivery.ProjectileSpec.straight(1.0 * variant.projectileSpeedFactor())
                        .homing(20.0F, 4).homingUntil(12).withLifetime(100).bursting(AreaShape.circle(4.0), 3.0)))
                .impact(new Impact.Damage(0.2F, 2.0F, true))
                .build();
        AbilitySpec nova = this.withVariantEffect(AbilitySpec.builder(id(prefix + "nova"), school)
                .visual("nova")
                .delivery(Delivery.Area.around(AreaShape.circle(6.0)))
                .damage(0.6F), variant, 3.0F, 80).build();

        CombatAction fireballCast = CombatAction.builder(FIREBALL_ID)
                .animation(CAST_QUICK)
                .timing(4, 9, 4)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(80)
                .onStart(ctx -> {
                    // Only a spell look has casting effects here; the built-in look stays as it was.
                    if (this.entity.level() instanceof ServerLevel level) {
                        AbilityPresentation.spellWindup(level, this.entity, fireball);
                    }
                })
                .onActiveStart(ctx -> this.castFireball(fireball))
                .onActiveTick(ctx -> {
                    // Second bolt of the same cast, with the casting animation replayed.
                    if (ctx.phaseTick() == 5) {
                        this.entity.playActionAnimation(RenegadeEntity.ACTION_CONTROLLER, CAST_QUICK);
                        this.castFireball(fireball);
                    }
                })
                .build();
        CombatAction volleyCast = CombatAction.builder(VOLLEY_ID)
                .animation(CAST_LONG)
                .timing(54, 6, 14)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.REDUCED, TrackingMode.REDUCED)
                .cooldown(640)
                .onStart(ctx -> {
                    LivingEntity target = ctx.liveTarget();
                    if (target != null && this.entity.level() instanceof ServerLevel level) {
                        AbilityExecutor.windup(level, this.entity, volleyBolt, CastTarget.at(this.entity, target));
                    }
                })
                .onActiveStart(ctx -> this.castVolley(ctx, volleyBolt))
                .build();
        CombatAction novaCast = CombatAction.builder(NOVA_ID)
                .animation(NOVA)
                .timing(16, 3, 11)
                .motion(AttackMotion.PLANTED)
                .tracking(TrackingMode.REDUCED, TrackingMode.LOCKED)
                .cooldown(440)
                .onStart(ctx -> {
                    ctx.locomotion().arrest(MovementProfile.HARD_STOP);
                    if (this.entity.level() instanceof ServerLevel level) {
                        AbilityExecutor.windup(level, this.entity, nova, CastTarget.at(this.entity, this.entity.position()));
                    }
                })
                .onActiveStart(ctx -> {
                    if (this.entity.level() instanceof ServerLevel level) {
                        AbilityExecutor.cast(level, this.entity, nova, CastTarget.at(this.entity, this.entity.position()));
                    }
                })
                .onFinish(ctx -> {
                    if (this.cooldowns.isReady(JUMP_BACK_ID)) {
                        this.chain(this.jumpBack);
                    }
                })
                .build();
        CombatAction flameSweep = CombatAction.builder(SWEEP_ID)
                .animation(FLAME_SWEEP)
                .timing(8, 4, 8)
                .motion(AttackMotion.PRESSING)
                .windupDrift(-0.3)
                .advance(-0.45)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(180)
                .volume(AttackVolume.sweep(SwingPath.rightToLeft(140.0F).withDynamics(SwingDynamics.FORCEFUL), 3.2, 2.4).withInnerRadius(0.3))
                .scaledDamage(0.55F)
                .knockback(0.45, 0.15)
                .slash(SlashVisual.light(school.particle()).withCount(24))
                .onActiveStart(ctx -> {
                    LivingEntity target = ctx.liveTarget();
                    if (target != null && this.entity.distanceTo(target) < 3.6F && this.entity.canHarm(target)) {
                        if (variant == WizardVariant.FIRE) {
                            target.igniteForSeconds(3.0F);
                        } else if (variant == WizardVariant.FROST) {
                            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, FROST_AMPLIFIER), this.entity);
                        }
                    }
                    if (this.entity.level() instanceof ServerLevel level) {
                        MagicParticles.pulse(level, school, this.entity.position().add(ctx.committedForward().scale(1.5)).add(0.0, 1.0, 0.0), 1.5F);
                        MagicParticles.sound(level, this.entity.position(), com.drehverschluss.renegades.magic.MagicSounds.of(school).release(), 1.5F, 1.0F);
                    }
                })
                .build();
        return new Spells(fireball, volleyBolt, nova, fireballCast, volleyCast, novaCast, flameSweep);
    }

    /**
     * Fire spells ignite (for the given seconds), frost spells slow (slowness II for the given ticks), arcane spells add
     * nothing.
     */
    private AbilitySpec.Builder withVariantEffect(AbilitySpec.Builder builder, WizardVariant variant, float igniteSeconds, int slowTicks) {
        return switch (variant) {
            case FIRE -> builder.impact(new Impact.Ignite(igniteSeconds));
            case FROST -> builder.impact(new Impact.ApplyEffect(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, FROST_AMPLIFIER));
            case ARCANE -> builder;
        };
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
                new Transition(Stance.HARASSMENT, s -> s.ownHealthPct < 0.5F, Stance.ZONING, 1),
                new Transition(Stance.ZONING, s -> s.timeSinceLastHit > 200, Stance.HARASSMENT, 1)
        );
    }

    @Override
    protected List<Transition> combatStateTransitions() {
        return List.of(
                new Transition(CombatState.PRESSURING, s -> s.targetDistance < BREACH_RANGE, CombatState.REPOSITIONING, 10),
                // Unlike the original, line of sight is not required here: a target hidden behind cover would otherwise
                // leave the wizard standing at spacing distance forever; pressuring walks around the cover instead.
                new Transition(CombatState.REPOSITIONING, s -> s.targetDistance >= SPACING_NEAR, CombatState.PRESSURING, 10)
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
        Vec3 targetPos = target.position();
        if (this.prevTargetPos != null && targetPos.distanceToSqr(this.prevTargetPos) < 0.01) {
            this.targetStationaryTicks++;
        } else {
            this.targetStationaryTicks = 0;
        }
        this.prevTargetPos = targetPos;
        if (--this.orbitFlipTimer <= 0) {
            this.orbitFlipTimer = 70 + this.entity.getRandom().nextInt(70);
            if (this.entity.getRandom().nextFloat() < 0.45F) {
                this.orbitDirection = -this.orbitDirection;
            }
        }

        if (this.actionOwnsMovement() || this.tryChain()) {
            return;
        }
        if (this.currentCombatState == CombatState.PRESSURING) {
            this.tickPressuring(target);
        } else {
            this.tickRepositioning(target);
        }
    }

    private void tickPressuring(LivingEntity target) {
        Spells spells = this.spells();
        boolean longCasting = this.actions.isBusy() && this.actions.current() == spells.volleyCast();
        MovementProfile profile = longCasting ? MovementProfile.CASTING_DRIFT : this.spacingProfile();
        this.locomotion.faceTarget(target, profile.turnDegrees());
        float distance = this.stimulus.targetDistance;
        if (!this.stimulus.lineOfSightToTarget && !longCasting) {
            this.locomotion.navigateTo(target.getX(), target.getY(), target.getZ(), 1.0);
        } else if (longCasting) {
            this.locomotion.orbit(target, ORBIT_RADIUS, this.orbitDirection, profile);
        } else if (distance > 12.0F) {
            this.locomotion.setFallbackGoal(target.position(), 1.0);
            this.locomotion.pressure(target, 9.0, profile);
        } else {
            this.locomotion.setFallbackGoal(target.position(), 1.0);
            this.locomotion.orbit(target, ORBIT_RADIUS, this.orbitDirection, profile);
        }

        if (this.actions.isBusy() || !this.stimulus.lineOfSightToTarget || distance > MAX_CAST_RANGE) {
            return;
        }
        if (distance < SPACING_NEAR && this.canNova()) {
            this.startAction(spells.novaCast());
        } else if (distance < 5.5F && this.cooldowns.isReady(SWEEP_ID) && this.canStrike(target, spells.flameSweep())) {
            this.startAction(spells.flameSweep());
        } else if (distance > MELEE_THRESHOLD && this.targetStationaryTicks >= 12 && this.cooldowns.isReady(VOLLEY_ID)) {
            this.startAction(spells.volleyCast());
        } else if (distance > MELEE_THRESHOLD && this.cooldowns.isReady(FIREBALL_ID)) {
            this.startAction(spells.fireballCast());
        }
    }

    private void tickRepositioning(LivingEntity target) {
        Spells spells = this.spells();
        MovementProfile profile = this.spacingProfile();
        this.locomotion.faceTarget(target, profile.turnDegrees());
        if (!this.actions.isBusy()) {
            if (this.stimulus.targetDistance < BREACH_RANGE && this.canNova() && this.stimulus.lineOfSightToTarget) {
                this.startAction(spells.novaCast());
                return;
            }
            if (this.cooldowns.isReady(JUMP_BACK_ID)) {
                this.startAction(this.jumpBack);
                return;
            }
            if (this.cooldowns.isReady(SWEEP_ID) && this.stimulus.targetDistance < 3.8F && this.canStrike(target, spells.flameSweep())) {
                this.startAction(spells.flameSweep());
                return;
            }
        }

        Vec3 exit = this.entity.position().add(this.locomotion.retreatVector(target).scale(8.0));
        this.locomotion.setFallbackGoal(exit, 1.3);
        this.locomotion.retreatFrom(target, SPACING_NEAR, profile.withMaxSpeed(profile.maxSpeed() * 1.2));
    }

    private boolean canNova() {
        return RenegadesConfig.ENABLE_NOVA.get() && this.cooldowns.isReady(NOVA_ID);
    }

    private MovementProfile spacingProfile() {
        return this.currentStance == Stance.ZONING ? MovementProfile.ELASTIC.urgent(1.12, 1.15, 1.05F) : MovementProfile.ELASTIC;
    }

    private void castFireball(AbilitySpec fireball) {
        LivingEntity target = this.entity.getTarget();
        if (target != null && this.entity.level() instanceof ServerLevel level && this.entity.hasLineOfSight(target)) {
            this.entity.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            AbilityExecutor.cast(level, this.entity, fireball, CastTarget.at(this.entity, target));
        }
    }

    /**
     * Four bolts launched upwards to the sides, two ticks apart, that then home in on the target.
     */
    private void castVolley(ActionContext ctx, AbilitySpec volleyBolt) {
        LivingEntity target = ctx.liveTarget();
        if (target == null || !(this.entity.level() instanceof ServerLevel level) || !this.entity.hasLineOfSight(target)) {
            return;
        }
        CastTarget aim = CastTarget.at(this.entity, target);
        for (int i = 0; i < VOLLEY_YAW_OFFSETS.length; i++) {
            Vec3 direction = Vec3.directionFromRotation(-45.0F, aim.yaw() + VOLLEY_YAW_OFFSETS[i]);
            int delay = i * 2;
            if (delay == 0) {
                AbilityExecutor.castProjectile(level, this.entity, volleyBolt, aim, direction);
            } else {
                TickScheduler.schedule(level, delay, () -> {
                    if (this.entity.isAlive()) {
                        AbilityExecutor.castProjectile(level, this.entity, volleyBolt, aim, direction);
                    }
                });
            }
        }
    }

    @Override
    public String describe() {
        WizardVariant variant = this.wizard.getVariant();
        double fireball = MagicBackends.basePower(this.entity, variant.school()) * FIREBALL_DAMAGE;
        return super.describe() + String.format(Locale.ROOT, ", variant %s, backend %s, fireball %.1f damage",
                variant.id(), MagicBackends.active().name(), fireball);
    }

    public enum CombatState implements BrainState {
        PRESSURING,
        REPOSITIONING;

        @Override
        public String id() {
            return this.name();
        }
    }

    public enum Stance implements BrainState {
        HARASSMENT,
        ZONING;

        @Override
        public String id() {
            return this.name();
        }
    }
}
