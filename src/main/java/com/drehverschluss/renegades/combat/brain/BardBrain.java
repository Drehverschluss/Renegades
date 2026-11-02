// Built on the combat and magic system ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0); positioning
// like PriestBrain; the abilities are new, modelled on the spells of Bards RPG (only their runtime ids are used).
package com.drehverschluss.renegades.combat.brain;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.combat.AttackMotion;
import com.drehverschluss.renegades.combat.AttackPhase;
import com.drehverschluss.renegades.combat.CombatAction;
import com.drehverschluss.renegades.combat.GroundLocomotion;
import com.drehverschluss.renegades.combat.MovementProfile;
import com.drehverschluss.renegades.combat.SlashVisual;
import com.drehverschluss.renegades.combat.TrackingMode;
import com.drehverschluss.renegades.combat.geometry.AttackVolume;
import com.drehverschluss.renegades.combat.geometry.SwingPath;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.BardRenegade;
import com.drehverschluss.renegades.entity.BardVariant;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.drehverschluss.renegades.magic.AbilityExecutor;
import com.drehverschluss.renegades.magic.AbilityPresentation;
import com.drehverschluss.renegades.magic.AbilitySpec;
import com.drehverschluss.renegades.magic.AreaShape;
import com.drehverschluss.renegades.magic.CastTarget;
import com.drehverschluss.renegades.magic.Delivery;
import com.drehverschluss.renegades.magic.ExternalEffects;
import com.drehverschluss.renegades.magic.Impact;
import com.drehverschluss.renegades.magic.ImpactContext;
import com.drehverschluss.renegades.magic.MagicBackends;
import com.drehverschluss.renegades.magic.MagicParticles;
import com.drehverschluss.renegades.magic.MagicSchool;
import com.drehverschluss.renegades.magic.MagicSounds;
import com.drehverschluss.renegades.magic.Relations;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;

/**
 * Bard combat, a supporter that makes its group stronger and its enemies weaker (the priest heals instead). It keeps
 * 8-14 blocks from its target, preferably behind an ally (alone further away), and jumps back from close targets.
 * <ul>
 * <li>song: while an ally nearby fights, it stands still and plays for 8 s; every 2 s a pulse around it hurts enemies a
 * little and gives itself and its allies a stack (up to 3) of +10 % attack damage (battle bard, lute) or -10 % damage
 * taken plus a small heal (guardian bard, lyre); hits interrupt it like the priest's heals;</li>
 * <li>mockery: vicious mockery on the target (less damage dealt, more damage taken);</li>
 * <li>crescendo: a sound wave that pierces up to 3 targets, stuns for at most 0.5 s and leaves them taking more
 * damage for 3 s;</li>
 * <li>encore: when an enemy is within 4 blocks, a blast that pushes it away; allies nearby get their running ability
 * cooldowns shortened by 20 %;</li>
 * <li>a jump back and a strike with the instrument as a last resort.</li>
 * </ul>
 */
public class BardBrain extends MobBrain {
    private static final String MAGE = "animation.v2.mage.";
    public static final String SONG = "animation.mob.wizard.staffwave";
    public static final String MOCKERY = "animation.mob.wave";
    public static final String CAST_QUICK = MAGE + "cast_quick";
    public static final String ENCORE = MAGE + "nova";
    public static final String JUMP_BACK = MAGE + "jump_back";
    public static final String STRIKE = "animation.mob.trickster.pommelstrike";
    /**
     * All triggered animations of this brain; SONG loops, the others play once.
     */
    public static final List<String> ACTION_ANIMATIONS = List.of(SONG, MOCKERY, CAST_QUICK, ENCORE, JUMP_BACK, STRIKE);

    private static final float MELEE_THRESHOLD = 4.0F;
    private static final float ENGAGEMENT_THRESHOLD = 20.0F;
    private static final float FLEE_THRESHOLD = 24.0F;
    private static final float DAMAGE_EXCEEDED_THRESHOLD = 8.0F;
    // Allies within this range steer the bard's position (behind them); the song needs one fighting within SONG_TRIGGER.
    private static final double SUPPORT_RANGE = 16.0;
    private static final double SONG_TRIGGER = 10.0;
    private static final double BAND_NEAR = 8.0;
    private static final double BAND_FAR = 14.0;
    private static final double ALONE_NEAR = 11.0;
    private static final double ALONE_FAR = 16.0;
    private static final double EVADE_RANGE = 4.0;
    private static final double EVADE_EXIT = 6.0;
    private static final double BEHIND_ALLY = 4.0;
    private static final float MAX_CAST_RANGE = 24.0F;
    private static final float INTERRUPT_DAMAGE = 4.0F;

    private static final double SONG_RADIUS = 10.0;
    private static final int SONG_TICKS = 160;
    private static final int SONG_PULSE = 40;
    private static final int SONG_STACKS = 3;
    private static final int SONG_BUFF_TICKS = 180;
    private static final float SONG_DAMAGE = 0.2F;
    private static final float SONG_HEAL = 0.3F;
    private static final float SONG_HEAL_CAP = 0.05F;
    private static final float MOCKERY_DAMAGE = 0.5F;
    private static final float MOCKERY_RANGE = 16.0F;
    private static final int MOCKERY_TICKS = 120;
    // Level 2 like the mod's spell: -10 % damage dealt, +10 % damage taken.
    private static final int MOCKERY_AMPLIFIER = 1;
    private static final float CRESCENDO_DAMAGE = 0.6F;
    private static final int CRESCENDO_PIERCE = 2;
    // The crescendo effect stuns for as long as it lasts, so it gets only 10 ticks; the higher damage taken lasts 3 s.
    private static final int CRESCENDO_STUN_TICKS = 10;
    private static final int CRESCENDO_EXPOSED_TICKS = 60;
    private static final double CRESCENDO_EXPOSED = 0.1;
    private static final ResourceLocation DAMAGE_TAKEN = ResourceLocation.fromNamespaceAndPath("spell_engine", "damage_taken");
    private static final ResourceLocation EXPOSED_ID = ResourceLocation.fromNamespaceAndPath(Renegades.MODID, "crescendo_exposed");
    private static final double ENCORE_RADIUS = 4.0;
    private static final float ENCORE_DAMAGE = 0.4F;
    private static final double ENCORE_ALLY_RADIUS = 8.0;
    private static final double ENCORE_COOLDOWN_FACTOR = 0.8;

    private static final String SONG_ID = "bard_song";
    private static final String MOCKERY_ID = "bard_mockery";
    private static final String CRESCENDO_ID = "bard_crescendo";
    private static final String ENCORE_ID = "bard_encore";
    private static final String JUMP_BACK_ID = "bard_jump_back";
    private static final String STRIKE_ID = "bard_strike";

    private final BardRenegade bard;
    private final Holder<MobEffect> mockery;
    private final Holder<MobEffect> crescendo;
    private final Holder<MobEffect> battleSongLook;
    private final Holder<MobEffect> guardianSongLook;
    private final Map<BardVariant, AbilitySpec> songs = new EnumMap<>(BardVariant.class);
    private final AbilitySpec mockerySpell;
    private final AbilitySpec crescendoSpell;
    private final AbilitySpec encoreSpell;
    private final CombatAction song;
    private final CombatAction mockeryCast;
    private final CombatAction crescendoCast;
    private final CombatAction encoreCast;
    private final CombatAction jumpBack;
    private final CombatAction strike;

    private List<RenegadeEntity> allies = List.of();
    private int allyScanCountdown;
    @Nullable
    private Vec3 positionGoal;
    private int goalCountdown;
    private int interruptions;
    private int songsPlayed;
    private double healedTotal;

    public BardBrain(BardRenegade entity) {
        super(entity, Stance.ALONE, CombatState.POSITIONING);
        this.bard = entity;
        this.mockery = ExternalEffects.orElse("bards_rpg:vicious_mockery", MobEffects.WEAKNESS);
        this.crescendo = ExternalEffects.orElse("bards_rpg:crescendo", MobEffects.MOVEMENT_SLOWDOWN);
        this.battleSongLook = ExternalEffects.orElse("bards_rpg:tale_of_the_dragonslayer", MobEffects.DAMAGE_BOOST);
        this.guardianSongLook = ExternalEffects.orElse("bards_rpg:troubadours_minuet", MobEffects.DAMAGE_RESISTANCE);

        this.mockerySpell = AbilitySpec.builder(id("bard/mockery"), MagicSchool.ARCANE)
                .visual("mockery")
                .impact(new Impact.Damage(MOCKERY_DAMAGE, 0.0F, true))
                .impact(new Impact.ApplyEffect(this.mockery, MOCKERY_TICKS, MOCKERY_AMPLIFIER))
                .build();
        this.crescendoSpell = AbilitySpec.builder(id("bard/crescendo"), MagicSchool.ARCANE)
                .visual("crescendo")
                .delivery(new Delivery.Projectile(Delivery.ProjectileSpec.straight(1.4).withLifetime(22).piercing(CRESCENDO_PIERCE)))
                .impact(new Impact.Damage(CRESCENDO_DAMAGE, 0.2F, true))
                .impact(new Impact.ApplyEffect(this.crescendo, CRESCENDO_STUN_TICKS, 0))
                .impact(new Impact.TimedAttribute(DAMAGE_TAKEN, EXPOSED_ID, CRESCENDO_EXPOSED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                        CRESCENDO_EXPOSED_TICKS))
                .build();
        this.encoreSpell = AbilitySpec.builder(id("bard/encore"), MagicSchool.ARCANE)
                .visual("encore")
                .noTelegraph()
                .delivery(Delivery.Area.around(AreaShape.circle(ENCORE_RADIUS)))
                .impact(new Impact.Damage(ENCORE_DAMAGE, 0.0F, true))
                .impact(new Impact.Knockback(1.3, 0.3))
                .build();

        this.song = CombatAction.builder(SONG_ID)
                .animation(SONG)
                .timing(10, SONG_TICKS, 6)
                .motion(AttackMotion.PLANTED)
                .tracking(TrackingMode.REDUCED, TrackingMode.REDUCED)
                .cooldown(400)
                .onStart(ctx -> {
                    if (this.entity.level() instanceof ServerLevel level) {
                        AbilityExecutor.windup(level, this.entity, this.songSpell(), CastTarget.at(this.entity, this.entity.position()));
                    }
                })
                .onActiveStart(ctx -> {
                    this.songsPlayed++;
                    this.songPulse();
                })
                .onActiveTick(ctx -> {
                    if (ctx.phaseTick() % SONG_PULSE == 0 && ctx.phaseTick() < SONG_TICKS) {
                        this.songPulse();
                    }
                })
                .onRecoveryStart(ctx -> this.entity.stopActionAnimation(RenegadeEntity.ACTION_CONTROLLER, SONG))
                .build();
        this.mockeryCast = CombatAction.builder(MOCKERY_ID)
                .animation(MOCKERY)
                .timing(12, 1, 8)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(300)
                .onStart(ctx -> this.spellWindup(this.mockerySpell))
                .onActiveStart(ctx -> this.castDirect(this.mockerySpell, MOCKERY_RANGE))
                .build();
        this.crescendoCast = CombatAction.builder(CRESCENDO_ID)
                .animation(CAST_QUICK)
                .timing(10, 1, 6)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(200)
                .onStart(ctx -> this.spellWindup(this.crescendoSpell))
                .onActiveStart(ctx -> this.shootCrescendo())
                .build();
        this.encoreCast = CombatAction.builder(ENCORE_ID)
                .animation(ENCORE)
                .timing(10, 1, 8)
                .motion(AttackMotion.PLANTED)
                .tracking(TrackingMode.FULL, TrackingMode.LOCKED)
                .cooldown(240)
                .onStart(ctx -> this.spellWindup(this.encoreSpell))
                .onActiveStart(ctx -> this.castEncore())
                .build();
        this.jumpBack = CombatAction.builder(JUMP_BACK_ID)
                .animation(JUMP_BACK)
                .timing(4, 12, 3)
                .motion(AttackMotion.RETREATING)
                .committed(0.42, 12, 0.46, 0.0)
                .tracking(TrackingMode.FULL, TrackingMode.MINIMAL)
                .cooldown(200)
                .onStart(ctx -> ctx.entity().playSound(SoundEvents.NOTE_BLOCK_FLUTE.value(), 0.8F, 1.4F))
                .build();
        this.strike = CombatAction.builder(STRIKE_ID)
                .animation(STRIKE)
                .timing(6, 3, 8)
                .motion(AttackMotion.PRESSING)
                .windupDrift(0.2)
                .advance(-0.3)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(160)
                .volume(AttackVolume.sweep(SwingPath.rightToLeft(100.0F), 2.8, 2.2))
                .scaledDamage(0.6F)
                .knockback(0.6, 0.15)
                .slash(SlashVisual.light(ParticleTypes.NOTE).withCount(6))
                .onStart(ctx -> ctx.entity().playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.6F))
                .build();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Renegades.MODID, path);
    }

    private BardVariant variant() {
        return this.bard.getVariant();
    }

    /**
     * The song as a spell: a pulse around the bard that hurts enemies (the allies' part is applied by songPulse).
     */
    private AbilitySpec songSpell() {
        return this.songs.computeIfAbsent(this.variant(), variant -> AbilitySpec.builder(id("bard/song_" + variant.id()), MagicSchool.ARCANE)
                .visual("song")
                .delivery(Delivery.Area.around(AreaShape.circle(SONG_RADIUS)))
                .impact(new Impact.Damage(SONG_DAMAGE, 0.0F, true))
                .build());
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
            this.allies = this.entity.level().getEntitiesOfClass(RenegadeEntity.class, this.entity.getBoundingBox().inflate(SUPPORT_RANGE),
                    other -> other != this.entity && other.distanceTo(this.entity) <= SUPPORT_RANGE && Relations.canHelp(this.entity, other));
        }
        if (this.actions.current() == this.song) {
            this.locomotion.faceTarget(target, 6.0F);
        }
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

    private void chooseAction(LivingEntity target) {
        float distance = this.stimulus.targetDistance;
        boolean sight = this.stimulus.lineOfSightToTarget;
        if (distance < ENCORE_RADIUS && sight && this.cooldowns.isReady(ENCORE_ID) && this.startFull(this.encoreCast)) {
            return;
        }
        if (this.currentCombatState == CombatState.EVADING && this.cooldowns.isReady(JUMP_BACK_ID) && this.startAction(this.jumpBack)) {
            return;
        }
        if (distance < 3.2F && this.cooldowns.isReady(STRIKE_ID) && this.canStrike(target, this.strike) && this.startAction(this.strike)) {
            return;
        }
        if (this.currentCombatState != CombatState.EVADING && RenegadesConfig.ENABLE_BARD_SONGS.get() && this.cooldowns.isReady(SONG_ID)
                && this.allies.stream().anyMatch(ally -> ally.getTarget() != null && ally.distanceTo(this.entity) <= SONG_TRIGGER) && this.startFull(this.song)) {
            return;
        }
        if (!sight || distance > MAX_CAST_RANGE) {
            return;
        }
        if (distance <= MOCKERY_RANGE && this.cooldowns.isReady(MOCKERY_ID) && !target.hasEffect(this.mockery) && this.startFull(this.mockeryCast)) {
            return;
        }
        if (this.cooldowns.isReady(CRESCENDO_ID)) {
            this.startFull(this.crescendoCast);
        }
    }

    /**
     * Support and control abilities keep their full cooldown, unlike the usual random shortening.
     */
    private boolean startFull(CombatAction action) {
        if (this.startAction(action)) {
            this.cooldowns.trigger(action.id(), action.cooldownTicks());
            return true;
        }
        return false;
    }

    /**
     * One pulse of the song: damage to enemies around the bard; itself and its allies there get a stack of the song
     * (and, from the guardian bard, a small heal).
     */
    private void songPulse() {
        if (!(this.entity.level() instanceof ServerLevel level)) {
            return;
        }
        AbilitySpec spec = this.songSpell();
        AbilityExecutor.cast(level, this.entity, spec, CastTarget.at(this.entity, this.entity.position()));
        boolean guardian = this.variant() == BardVariant.GUARDIAN;
        ImpactContext context = ImpactContext.of(this.entity, MagicSchool.ARCANE, this.entity.position(), null);
        Impact.Heal heal = new Impact.Heal(SONG_HEAL, SONG_HEAL_CAP);
        List<RenegadeEntity> listeners = this.entity.level().getEntitiesOfClass(RenegadeEntity.class, this.entity.getBoundingBox().inflate(SONG_RADIUS),
                other -> other.distanceTo(this.entity) <= SONG_RADIUS && Relations.canHelp(this.entity, other));
        for (RenegadeEntity listener : listeners) {
            int stacks = listener.addSongStack(guardian, SONG_STACKS, SONG_BUFF_TICKS);
            // The mod's song effect only as a visible marker (its own bonus is small: +1 % attack or -3 % damage taken).
            listener.addEffect(new MobEffectInstance(guardian ? this.guardianSongLook : this.battleSongLook, SONG_BUFF_TICKS, 0, false, true), this.entity);
            if (guardian && RenegadesConfig.ENABLE_PRIEST_HEALING.get()) {
                heal.apply(context, listener);
            }
            if (!AbilityPresentation.spellImpact(level, this.entity, spec, listener).particles()) {
                Vec3 at = listener.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.NOTE, at.x, at.y + 1.0, at.z, stacks, 0.3, 0.3, 0.3, 0.5);
            }
        }
    }

    private void spellWindup(AbilitySpec spec) {
        if (this.entity.level() instanceof ServerLevel level) {
            AbilityPresentation.spellWindup(level, this.entity, spec);
        }
    }

    private void castDirect(AbilitySpec spec, float range) {
        LivingEntity target = this.entity.getTarget();
        if (target == null || !target.isAlive() || !(this.entity.level() instanceof ServerLevel level) || !this.entity.hasLineOfSight(target)
                || this.entity.distanceTo(target) > range) {
            return;
        }
        this.entity.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        AbilityExecutor.cast(level, this.entity, spec, CastTarget.at(this.entity, target));
    }

    private void shootCrescendo() {
        LivingEntity target = this.entity.getTarget();
        if (target != null && this.entity.level() instanceof ServerLevel level && this.entity.hasLineOfSight(target)) {
            this.entity.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            AbilityExecutor.cast(level, this.entity, this.crescendoSpell, CastTarget.at(this.entity, target));
        }
    }

    /**
     * Encore: the blast around the bard, and the allies within 8 blocks get their running ability cooldowns shortened.
     */
    private void castEncore() {
        if (!(this.entity.level() instanceof ServerLevel level)) {
            return;
        }
        AbilityExecutor.cast(level, this.entity, this.encoreSpell, CastTarget.at(this.entity, this.entity.position()));
        for (RenegadeEntity ally : this.entity.level().getEntitiesOfClass(RenegadeEntity.class, this.entity.getBoundingBox().inflate(ENCORE_ALLY_RADIUS),
                other -> other != this.entity && other.distanceTo(this.entity) <= ENCORE_ALLY_RADIUS && Relations.canHelp(this.entity, other))) {
            if (ally.getCombatBrain() != null) {
                ally.getCombatBrain().cooldowns.scaleRunning(ENCORE_COOLDOWN_FACTOR);
                Vec3 at = ally.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.NOTE, at.x, at.y + 1.0, at.z, 4, 0.3, 0.3, 0.3, 0.6);
            }
        }
    }

    /**
     * Keeps 8-14 blocks from the target, preferably behind an ally; alone 11-16 blocks; backs off when the target is
     * too close.
     */
    private void move(LivingEntity target) {
        MovementProfile profile = MovementProfile.ELASTIC;
        this.locomotion.faceTarget(target, profile.turnDegrees());
        boolean alone = this.allies.isEmpty();
        double near = alone ? ALONE_NEAR : BAND_NEAR;
        double far = alone ? ALONE_FAR : BAND_FAR;
        double distance = GroundLocomotion.horizontal(target.position().subtract(this.entity.position())).length();
        if (this.currentCombatState == CombatState.EVADING || distance < near) {
            this.locomotion.setFallbackGoal(this.entity.position().add(this.locomotion.retreatVector(target).scale(8.0)), 1.3);
            this.locomotion.retreatFrom(target, near, profile.withMaxSpeed(profile.maxSpeed() * 1.2));
            return;
        }
        if (!this.stimulus.lineOfSightToTarget && alone) {
            this.locomotion.navigateTo(target.getX(), target.getY(), target.getZ(), 1.0);
            return;
        }
        if (--this.goalCountdown <= 0 || this.positionGoal == null) {
            this.goalCountdown = 10;
            this.positionGoal = this.choosePosition(target, distance, far);
        }
        Vec3 goal = this.positionGoal;
        if (goal == null || GroundLocomotion.horizontal(goal.subtract(this.entity.position())).length() < 1.2) {
            this.locomotion.arrest(profile);
            return;
        }
        this.locomotion.seekPosition(goal, profile);
    }

    @Nullable
    private Vec3 choosePosition(LivingEntity target, double distance, double far) {
        RenegadeEntity anchor = this.allies.stream()
                .filter(ally -> ally.distanceTo(target) < 10.0F)
                .min(Comparator.comparingDouble(ally -> ally.distanceToSqr(target)))
                .orElse(null);
        if (anchor != null) {
            Vec3 away = GroundLocomotion.horizontal(anchor.position().subtract(target.position()));
            if (away.lengthSqr() > 2.5 * 2.5) {
                double range = Mth.clamp(away.length() + BEHIND_ALLY, 9.0, 13.0);
                return target.position().add(away.normalize().scale(range));
            }
            // The ally fights right at the target: stay at 9 blocks on the own side, so the song still reaches it.
            Vec3 own = GroundLocomotion.horizontal(this.entity.position().subtract(target.position()));
            if (Math.abs(distance - 9.0) > 1.5 && own.lengthSqr() > 1.0E-4) {
                return target.position().add(own.normalize().scale(9.0));
            }
            return null;
        }
        if (distance <= far) {
            return null;
        }
        Vec3 away = GroundLocomotion.horizontal(this.entity.position().subtract(target.position()));
        return away.lengthSqr() < 1.0E-4 ? null : target.position().add(away.normalize().scale(far - 1.0));
    }

    /**
     * A hit of 4+ damage or any melee hit while the song plays (windup or playing) ends it; its cooldown then runs at
     * half length.
     */
    @Override
    public void onDamageTaken(DamageSource source, float amount) {
        super.onDamageTaken(source, amount);
        if (this.actions.current() != this.song || this.actions.phase() == AttackPhase.RECOVERY) {
            return;
        }
        boolean melee = source.getEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()
                && !source.is(DamageTypeTags.IS_PROJECTILE);
        if (amount < INTERRUPT_DAMAGE && !melee) {
            return;
        }
        this.actions.cancel();
        this.cooldowns.trigger(SONG_ID, this.song.cooldownTicks() / 2);
        this.interruptions++;
        if (this.entity.level() instanceof ServerLevel level) {
            Vec3 at = this.entity.position();
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 1.4, at.z, 10, 0.3, 0.3, 0.3, 0.02);
            MagicParticles.sound(level, at, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1.0F, 0.6F);
        }
    }

    @Override
    public void onTargetLost() {
        super.onTargetLost();
        this.positionGoal = null;
    }

    public void recordHeal(float amount) {
        this.healedTotal += amount;
    }

    @Override
    public String describe() {
        double power = MagicBackends.basePower(this.entity, MagicSchool.ARCANE);
        return super.describe() + String.format(Locale.ROOT,
                ", variant %s, backend %s, power %.1f, songs %d, interrupted %d, healed %.1f HP, allies %d, cooldowns song %d / mockery %d / crescendo %d / encore %d, own song stacks %d/%d",
                this.variant().id(), MagicBackends.active().name(), power, this.songsPlayed, this.interruptions, this.healedTotal, this.allies.size(),
                this.cooldowns.remaining(SONG_ID), this.cooldowns.remaining(MOCKERY_ID), this.cooldowns.remaining(CRESCENDO_ID),
                this.cooldowns.remaining(ENCORE_ID), this.entity.battleSongStacks(), this.entity.guardianSongStacks());
    }

    public enum CombatState implements BrainState {
        POSITIONING,
        EVADING;

        @Override
        public String id() {
            return this.name();
        }
    }

    public enum Stance implements BrainState {
        ALONE,
        SUPPORTING;

        @Override
        public String id() {
            return this.name();
        }
    }
}
