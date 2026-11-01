// Movement and spacing follow WizardBrain (based on FireMageBrain from ANARCHY - RPG Minibosses by cleannrooster,
// CC0-1.0); the spells are new, modelled on the classes of Elemental Wizards RPG (only their runtime ids are used).
package com.drehverschluss.renegades.combat.brain;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.combat.AttackMotion;
import com.drehverschluss.renegades.combat.AttackPhase;
import com.drehverschluss.renegades.combat.CombatAction;
import com.drehverschluss.renegades.combat.MovementProfile;
import com.drehverschluss.renegades.combat.SlashVisual;
import com.drehverschluss.renegades.combat.TrackingMode;
import com.drehverschluss.renegades.combat.geometry.AttackVolume;
import com.drehverschluss.renegades.combat.geometry.SwingDynamics;
import com.drehverschluss.renegades.combat.geometry.SwingPath;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.ElementalVariant;
import com.drehverschluss.renegades.entity.ElementalWizardRenegade;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Elemental wizard combat, a control caster: keeps 6-9 blocks like the wizard, jumps back from close targets and hits
 * with its staff as a last resort. Its element decides the spells:
 * <ul>
 * <li>aqua: water ball volley, water whip (soaked) and springwater (damage and soaked around it, heals itself and its
 * allies; interrupted by hits during the windup);</li>
 * <li>terra: piercing stone spear (bleed), impale (announced on the ground under the target, then holds it in place)
 * and stone skin (stone flesh for itself and its allies when one of them is attacked);</li>
 * <li>wind: air cutter (strong knockback), aeroblast (throws a close target up, with slow falling) and windfield (strong
 * slowness where the target stands; vanilla slowness, see WINDFIELD_SLOWNESS); jumps back more often and also steps
 * aside.</li>
 * </ul>
 * Status effects come from Elemental Wizards RPG, mrpg_lib and Spell Engine by registry id; without them (class not
 * available, e.g. an old saved renegade) the nearest vanilla effects are used.
 */
public class ElementalWizardBrain extends MobBrain {
    private static final String FAMILY = "animation.v2.mage.";
    public static final String CAST_QUICK = FAMILY + "cast_quick";
    public static final String CAST_LONG = FAMILY + "cast_long";
    public static final String NOVA = FAMILY + "nova";
    public static final String JUMP_BACK = FAMILY + "jump_back";
    public static final String STAFF_SWING = FAMILY + "flame_sweep";
    public static final String HEAL = "animation.templar_heal";
    /**
     * All triggered animations of this brain; registered on the renegade's action controller on both sides.
     */
    public static final List<String> ACTION_ANIMATIONS = List.of(CAST_QUICK, CAST_LONG, NOVA, JUMP_BACK, STAFF_SWING, HEAL);
    public static final List<String> LOOPED_ANIMATIONS = List.of(HEAL);

    private static final float MELEE_THRESHOLD = 4.0F;
    private static final float ENGAGEMENT_THRESHOLD = 20.0F;
    private static final float FLEE_THRESHOLD = 24.0F;
    private static final float DAMAGE_EXCEEDED_THRESHOLD = 10.0F;
    private static final double ORBIT_RADIUS = 7.5;
    private static final double BREACH_RANGE = 4.5;
    private static final double SPACING_NEAR = 6.0;
    private static final float MAX_CAST_RANGE = 24.0F;
    private static final int RECENTLY_HIT_TICKS = 40;
    private static final float INTERRUPT_DAMAGE = 4.0F;

    // Aqua
    private static final float VOLLEY_DAMAGE = 0.18F;
    private static final int VOLLEY_SHOTS = 3;
    private static final int VOLLEY_SPACING = 4;
    private static final float WHIP_DAMAGE = 0.35F;
    private static final float WHIP_RANGE = 12.0F;
    private static final double SPRING_RADIUS = 5.0;
    private static final float SPRING_DAMAGE = 0.5F;
    private static final float SPRING_HEAL = 1.0F;
    private static final float SPRING_HEAL_CAP = 0.15F;
    private static final float SPRING_BELOW = 0.6F;
    private static final int SOAKED_TICKS = 80;
    private static final int CLEANSING_TICKS = 60;
    // Terra
    private static final float SPEAR_DAMAGE = 0.8F;
    private static final int SPEAR_PIERCE = 3;
    private static final int BLEED_TICKS = 60;
    private static final float IMPALE_DAMAGE = 0.5F;
    private static final double IMPALE_RADIUS = 1.5;
    private static final float IMPALE_RANGE = 16.0F;
    private static final int IMPALE_WINDUP = 20;
    private static final int IMPALED_TICKS = 20;
    private static final double STONE_SKIN_RADIUS = 5.0;
    private static final int STONE_FLESH_TICKS = 200;
    // Each level adds 1 armor and 0.25 armor toughness (the spell scales it with the caster's spell power).
    private static final int STONE_FLESH_AMPLIFIER = 3;
    // Wind
    private static final float CUTTER_DAMAGE = 0.6F;
    private static final float CUTTER_RANGE = 16.0F;
    private static final float AEROBLAST_DAMAGE = 0.4F;
    private static final float AEROBLAST_RANGE = 6.0F;
    private static final double AEROBLAST_LIFT = 1.2;
    private static final int SLOW_FALLING_TICKS = 120;
    private static final float WINDFIELD_DAMAGE = 0.3F;
    private static final double WINDFIELD_RADIUS = 4.0;
    private static final float WINDFIELD_RANGE = 16.0F;
    private static final int WINDFIELD_TICKS = 100;
    // Slowness IV (-60 %) instead of the mod's windfield effect (-70 %): that effect pushes an airborne target up every
    // tick (it shares the updraft code), so a player thrown by aeroblast hovers until the server kicks him for flying.
    private static final int WINDFIELD_SLOWNESS = 3;

    private static final String VOLLEY_ID = "elemental_water_volley";
    private static final String WHIP_ID = "elemental_water_whip";
    private static final String SPRING_ID = "elemental_springwater";
    private static final String SPEAR_ID = "elemental_stone_spear";
    private static final String IMPALE_ID = "elemental_impale";
    private static final String STONE_SKIN_ID = "elemental_stone_skin";
    private static final String CUTTER_ID = "elemental_air_cutter";
    private static final String AEROBLAST_ID = "elemental_aeroblast";
    private static final String WINDFIELD_ID = "elemental_windfield";
    private static final String JUMP_BACK_ID = "elemental_jump_back";
    private static final String SIDESTEP_ID = "elemental_sidestep";
    private static final String STAFF_ID = "elemental_staff_swing";

    private final ElementalWizardRenegade wizard;
    private final Map<ElementalVariant, Kit> kits = new EnumMap<>(ElementalVariant.class);
    private final Holder<MobEffect> soaked;
    private final Holder<MobEffect> cleansingWater;
    private final Holder<MobEffect> bleed;
    private final Holder<MobEffect> impaled;
    private final Holder<MobEffect> stoneFlesh;
    @Nullable
    private CombatAction jumpBack;
    @Nullable
    private CombatAction sidestep;
    @Nullable
    private ElementalVariant movesFor;
    private List<RenegadeEntity> nearbyAllies = List.of();
    private int allyScanCountdown;
    // Ground spot of a running impale or windfield, fixed when its windup starts (so it can be dodged).
    @Nullable
    private Vec3 lockedSpot;
    private int targetStationaryTicks;
    @Nullable
    private Vec3 prevTargetPos;
    private int orbitDirection = 1;
    private int orbitFlipTimer;
    private int interruptions;
    private double healedTotal;

    /**
     * Spells and actions of one element: a main spell (always the fallback at range), a second spell and a special.
     */
    private record Kit(AbilitySpec main, AbilitySpec second, AbilitySpec special, CombatAction mainCast, CombatAction secondCast,
                       CombatAction specialCast, CombatAction staffSwing) {
    }

    public ElementalWizardBrain(ElementalWizardRenegade entity) {
        super(entity, WizardBrain.Stance.HARASSMENT, WizardBrain.CombatState.PRESSURING);
        this.wizard = entity;
        this.soaked = ExternalEffects.orElse("mrpg_lib:soaked", MobEffects.MOVEMENT_SLOWDOWN);
        this.cleansingWater = ExternalEffects.orElse("elemental_wizards_rpg:cleansing_water", MobEffects.REGENERATION);
        this.bleed = ExternalEffects.orElse("spell_engine:bleed", MobEffects.POISON);
        this.impaled = ExternalEffects.orElse("elemental_wizards_rpg:impaled", MobEffects.MOVEMENT_SLOWDOWN);
        this.stoneFlesh = ExternalEffects.orElse("elemental_wizards_rpg:stone_flesh", MobEffects.DAMAGE_RESISTANCE);
    }

    private ElementalVariant variant() {
        return this.wizard.getVariant();
    }

    private Kit kit() {
        return this.kits.computeIfAbsent(this.variant(), this::createKit);
    }

    /**
     * Jump back and side step depend on the element (wind dodges more often); rebuilt if the element changed.
     */
    private void ensureMoves() {
        ElementalVariant variant = this.variant();
        if (this.movesFor == variant && this.jumpBack != null) {
            return;
        }
        this.movesFor = variant;
        boolean wind = variant == ElementalVariant.WIND;
        this.jumpBack = CombatAction.builder(JUMP_BACK_ID)
                .animation(JUMP_BACK)
                .timing(4, 12, 3)
                .motion(AttackMotion.RETREATING)
                .committed(0.42, 12, 0.46, 0.0)
                .tracking(TrackingMode.FULL, TrackingMode.MINIMAL)
                .cooldown(wind ? 200 : 400)
                .onStart(ctx -> ctx.entity().playSound(wind ? SoundEvents.BREEZE_JUMP : SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 1.3F))
                .onFinish(ctx -> {
                    LivingEntity target = ctx.liveTarget();
                    if (target != null) {
                        ctx.locomotion().faceTarget(target, 90.0F);
                    }
                    if (this.cooldowns.isReady(this.kit().mainCast().id())) {
                        this.chain(this.kit().mainCast());
                    }
                })
                .build();
        this.sidestep = wind ? CombatAction.builder(SIDESTEP_ID)
                .timing(2, 8, 2)
                .motion(AttackMotion.SIDESTEP)
                .committed(0.5, 8, 0.0, 0.0)
                .tracking(TrackingMode.FULL, TrackingMode.FULL)
                .cooldown(100)
                .onStart(ctx -> {
                    ctx.setScratch(this.entity.getRandom().nextBoolean() ? 1 : -1);
                    ctx.entity().playSound(SoundEvents.BREEZE_SLIDE, 0.8F, 1.2F);
                    if (this.entity.level() instanceof ServerLevel level) {
                        Vec3 at = this.entity.position();
                        level.sendParticles(MagicSchool.AIR.particle(), at.x, at.y + 0.2, at.z, 6, 0.3, 0.05, 0.3, 0.02);
                    }
                })
                .build() : null;
    }

    private Kit createKit(ElementalVariant variant) {
        MagicSchool school = variant.school();
        String prefix = "elemental_wizard/" + variant.id() + "/";
        CombatAction staffSwing = CombatAction.builder(STAFF_ID)
                .animation(STAFF_SWING)
                .timing(8, 4, 8)
                .motion(AttackMotion.PRESSING)
                .windupDrift(-0.3)
                .advance(-0.45)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(180)
                .volume(AttackVolume.sweep(SwingPath.rightToLeft(140.0F).withDynamics(SwingDynamics.FORCEFUL), 3.2, 2.4).withInnerRadius(0.3))
                .scaledDamage(0.55F)
                .knockback(0.45, 0.15)
                .slash(SlashVisual.light(school.particle()).withCount(16))
                .build();
        return switch (variant) {
            case AQUA -> this.aquaKit(school, prefix, staffSwing);
            case TERRA -> this.terraKit(school, prefix, staffSwing);
            case WIND -> this.windKit(school, prefix, staffSwing);
        };
    }

    private Kit aquaKit(MagicSchool school, String prefix, CombatAction staffSwing) {
        AbilitySpec volley = AbilitySpec.builder(id(prefix + "water_volley"), school)
                .visual("water_volley")
                .delivery(new Delivery.Projectile(Delivery.ProjectileSpec.straight(1.5).withLifetime(24)))
                .impact(new Impact.Damage(VOLLEY_DAMAGE, 0.2F, true))
                .build();
        AbilitySpec whip = AbilitySpec.builder(id(prefix + "water_whip"), school)
                .visual("water_whip")
                .impact(new Impact.Damage(WHIP_DAMAGE, 0.3F, true))
                .impact(new Impact.ApplyEffect(this.soaked, SOAKED_TICKS, 0))
                .build();
        AbilitySpec spring = AbilitySpec.builder(id(prefix + "springwater"), school)
                .visual("springwater")
                .delivery(Delivery.Area.around(AreaShape.circle(SPRING_RADIUS)))
                .impact(Impact.Damage.of(SPRING_DAMAGE))
                .impact(new Impact.ApplyEffect(this.soaked, SOAKED_TICKS, 0))
                .allyImpact(new Impact.Heal(SPRING_HEAL, SPRING_HEAL_CAP))
                .allyImpact(new Impact.ApplyEffect(this.cleansingWater, CLEANSING_TICKS, 0))
                .build();

        CombatAction volleyCast = CombatAction.builder(VOLLEY_ID)
                .animation(CAST_QUICK)
                .timing(6, VOLLEY_SPACING * (VOLLEY_SHOTS - 1) + 1, 4)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(100)
                .onStart(ctx -> this.spellWindup(volley))
                .onActiveStart(ctx -> this.shoot(volley))
                .onActiveTick(ctx -> {
                    if (ctx.phaseTick() % VOLLEY_SPACING == 0 && ctx.phaseTick() < VOLLEY_SPACING * VOLLEY_SHOTS) {
                        this.entity.playActionAnimation(RenegadeEntity.ACTION_CONTROLLER, CAST_QUICK);
                        this.shoot(volley);
                    }
                })
                .build();
        CombatAction whipCast = CombatAction.builder(WHIP_ID)
                .animation(CAST_QUICK)
                .timing(8, 1, 6)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(140)
                .onStart(ctx -> this.spellWindup(whip))
                .onActiveStart(ctx -> this.castDirect(whip, WHIP_RANGE))
                .build();
        CombatAction springCast = CombatAction.builder(SPRING_ID)
                .animation(HEAL)
                .timing(30, 1, 8)
                .motion(AttackMotion.PLANTED)
                .tracking(TrackingMode.LOCKED, TrackingMode.LOCKED)
                .cooldown(400)
                .onStart(ctx -> {
                    if (this.entity.level() instanceof ServerLevel level) {
                        AbilityExecutor.windup(level, this.entity, spring, CastTarget.at(this.entity, this.entity.position()));
                    }
                })
                .onActiveStart(ctx -> this.castSpringwater(spring))
                .onRecoveryStart(ctx -> this.entity.stopActionAnimation(RenegadeEntity.ACTION_CONTROLLER, HEAL))
                .build();
        return new Kit(volley, whip, spring, volleyCast, whipCast, springCast, staffSwing);
    }

    private Kit terraKit(MagicSchool school, String prefix, CombatAction staffSwing) {
        AbilitySpec spear = AbilitySpec.builder(id(prefix + "stone_spear"), school)
                .visual("stone_spear")
                .delivery(new Delivery.Projectile(Delivery.ProjectileSpec.straight(0.9).withLifetime(40).piercing(SPEAR_PIERCE)))
                .impact(new Impact.Damage(SPEAR_DAMAGE, 0.3F, true))
                .impact(new Impact.ApplyEffect(this.bleed, BLEED_TICKS, 0))
                .build();
        AbilitySpec impale = AbilitySpec.builder(id(prefix + "impale"), school)
                .visual("impale")
                .delivery(new Delivery.Area(AreaShape.circle(IMPALE_RADIUS), Delivery.Anchor.TARGET, 2.5))
                .impact(Impact.Damage.of(IMPALE_DAMAGE))
                .impact(new Impact.ApplyEffect(this.impaled, IMPALED_TICKS, 0))
                .build();
        // Helpful area around the wizard: reaches itself and its allies (Relations#canHelp).
        AbilitySpec stoneSkin = AbilitySpec.builder(id(prefix + "stone_skin"), school)
                .helpful()
                .visual("stone_skin")
                .noTelegraph()
                .delivery(Delivery.Area.around(AreaShape.circle(STONE_SKIN_RADIUS)))
                .impact(new Impact.ApplyEffect(this.stoneFlesh, STONE_FLESH_TICKS, STONE_FLESH_AMPLIFIER))
                .build();

        CombatAction spearCast = CombatAction.builder(SPEAR_ID)
                .animation(CAST_QUICK)
                .timing(12, 1, 6)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(110)
                .onStart(ctx -> this.spellWindup(spear))
                .onActiveStart(ctx -> this.shoot(spear))
                .build();
        CombatAction impaleCast = CombatAction.builder(IMPALE_ID)
                .animation(CAST_LONG)
                .timing(IMPALE_WINDUP, 1, 8)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.REDUCED, TrackingMode.REDUCED)
                .cooldown(240)
                .onStart(ctx -> this.lockSpot(impale, ctx.liveTarget()))
                .onActiveStart(ctx -> this.castAtSpot(impale))
                .build();
        CombatAction stoneSkinCast = CombatAction.builder(STONE_SKIN_ID)
                .animation(CAST_QUICK)
                .timing(8, 1, 6)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.REDUCED, TrackingMode.REDUCED)
                .cooldown(500)
                .onStart(ctx -> this.spellWindup(stoneSkin))
                .onActiveStart(ctx -> {
                    if (this.entity.level() instanceof ServerLevel level) {
                        AbilityExecutor.cast(level, this.entity, stoneSkin, CastTarget.at(this.entity, this.entity.position()));
                    }
                })
                .build();
        return new Kit(spear, impale, stoneSkin, spearCast, impaleCast, stoneSkinCast, staffSwing);
    }

    private Kit windKit(MagicSchool school, String prefix, CombatAction staffSwing) {
        AbilitySpec cutter = AbilitySpec.builder(id(prefix + "air_cutter"), school)
                .visual("air_cutter")
                .impact(new Impact.Damage(CUTTER_DAMAGE, 0.0F, true))
                .impact(new Impact.Knockback(1.4, 0.25))
                .build();
        AbilitySpec windfieldSpell = AbilitySpec.builder(id(prefix + "windfield"), school)
                .visual("windfield")
                .delivery(Delivery.Area.at(AreaShape.circle(WINDFIELD_RADIUS)))
                .impact(Impact.Damage.of(WINDFIELD_DAMAGE))
                .impact(new Impact.ApplyEffect(MobEffects.MOVEMENT_SLOWDOWN, WINDFIELD_TICKS, WINDFIELD_SLOWNESS))
                .build();
        AbilitySpec aeroblast = AbilitySpec.builder(id(prefix + "aeroblast"), school)
                .visual("aeroblast")
                .impact(new Impact.Damage(AEROBLAST_DAMAGE, 0.0F, true))
                .impact(new Impact.Launch(AEROBLAST_LIFT, SLOW_FALLING_TICKS))
                .build();

        CombatAction cutterCast = CombatAction.builder(CUTTER_ID)
                .animation(CAST_QUICK)
                .timing(6, 1, 5)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.FULL, TrackingMode.REDUCED)
                .cooldown(75)
                .onStart(ctx -> this.spellWindup(cutter))
                .onActiveStart(ctx -> this.castDirect(cutter, CUTTER_RANGE))
                .build();
        CombatAction windfieldCast = CombatAction.builder(WINDFIELD_ID)
                .animation(CAST_LONG)
                .timing(16, 1, 8)
                .motion(AttackMotion.MOBILE)
                .tracking(TrackingMode.REDUCED, TrackingMode.REDUCED)
                .cooldown(360)
                .onStart(ctx -> this.lockSpot(windfieldSpell, ctx.liveTarget()))
                .onActiveStart(ctx -> this.castAtSpot(windfieldSpell))
                .build();
        CombatAction aeroblastCast = CombatAction.builder(AEROBLAST_ID)
                .animation(NOVA)
                .timing(10, 1, 8)
                .motion(AttackMotion.PLANTED)
                .tracking(TrackingMode.FULL, TrackingMode.LOCKED)
                .cooldown(300)
                .onStart(ctx -> this.spellWindup(aeroblast))
                .onActiveStart(ctx -> this.castDirect(aeroblast, AEROBLAST_RANGE + 1.5F))
                .build();
        return new Kit(cutter, windfieldSpell, aeroblast, cutterCast, windfieldCast, aeroblastCast, staffSwing);
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
                new Transition(WizardBrain.Stance.HARASSMENT, s -> s.ownHealthPct < 0.5F, WizardBrain.Stance.ZONING, 1),
                new Transition(WizardBrain.Stance.ZONING, s -> s.timeSinceLastHit > 200, WizardBrain.Stance.HARASSMENT, 1)
        );
    }

    @Override
    protected List<Transition> combatStateTransitions() {
        return List.of(
                new Transition(WizardBrain.CombatState.PRESSURING, s -> s.targetDistance < BREACH_RANGE, WizardBrain.CombatState.REPOSITIONING, 10),
                new Transition(WizardBrain.CombatState.REPOSITIONING, s -> s.targetDistance >= SPACING_NEAR, WizardBrain.CombatState.PRESSURING, 10)
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
        this.ensureMoves();
        if (--this.allyScanCountdown <= 0) {
            this.allyScanCountdown = 5;
            this.nearbyAllies = this.entity.level().getEntitiesOfClass(RenegadeEntity.class, this.entity.getBoundingBox().inflate(SPRING_RADIUS),
                    other -> other != this.entity && other.distanceTo(this.entity) <= SPRING_RADIUS && Relations.canHelp(this.entity, other));
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
        this.tickRunningCast();

        if (this.actionOwnsMovement() || this.tryChain()) {
            return;
        }
        if (this.currentCombatState == WizardBrain.CombatState.PRESSURING) {
            this.tickPressuring(target);
        } else {
            this.tickRepositioning(target);
        }
    }

    /**
     * While impale or windfield winds up: the announced spot is marked on the ground every few ticks.
     */
    private void tickRunningCast() {
        CombatAction current = this.actions.current();
        if (current == null || this.lockedSpot == null || this.actions.phase() != AttackPhase.WINDUP
                || !(this.entity.level() instanceof ServerLevel level)) {
            return;
        }
        Kit kit = this.kit();
        if (current == kit.secondCast() && this.variant() != ElementalVariant.AQUA && this.actions.phaseTick() % 4 == 0) {
            float radius = (float) ((Delivery.Area) kit.second().delivery()).shape().extent();
            MagicParticles.ring(level, kit.second().school(), this.lockedSpot, radius);
            if (this.variant() == ElementalVariant.TERRA) {
                level.sendParticles(MagicSchool.EARTH.particle(), this.lockedSpot.x, this.lockedSpot.y + 0.1, this.lockedSpot.z, 8,
                        radius * 0.5, 0.05, radius * 0.5, 0.0);
            }
        }
    }

    private void tickPressuring(LivingEntity target) {
        Kit kit = this.kit();
        boolean longCasting = this.actions.isBusy() && this.actions.current() == kit.secondCast() && this.variant() != ElementalVariant.AQUA;
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

        if (this.actions.isBusy()) {
            return;
        }
        if (this.trySupport(distance)) {
            return;
        }
        if (!this.stimulus.lineOfSightToTarget || distance > MAX_CAST_RANGE) {
            return;
        }
        if (this.tryCloseSpecial(target, distance)) {
            return;
        }
        if (distance < 5.5F && this.cooldowns.isReady(STAFF_ID) && this.canStrike(target, kit.staffSwing())) {
            this.startAction(kit.staffSwing());
            return;
        }
        if (this.trySecond(target, distance)) {
            return;
        }
        if (distance > MELEE_THRESHOLD && this.cooldowns.isReady(kit.mainCast().id()) && this.inMainRange(distance)) {
            this.startAction(kit.mainCast());
            return;
        }
        if (this.sidestep != null && this.cooldowns.isReady(SIDESTEP_ID) && (distance < 8.0F || this.stimulus.timeSinceLastHit < RECENTLY_HIT_TICKS)) {
            this.startAction(this.sidestep);
        }
    }

    private void tickRepositioning(LivingEntity target) {
        Kit kit = this.kit();
        MovementProfile profile = this.spacingProfile();
        this.locomotion.faceTarget(target, profile.turnDegrees());
        if (!this.actions.isBusy()) {
            float distance = this.stimulus.targetDistance;
            if (this.trySupport(distance)) {
                return;
            }
            if (this.stimulus.lineOfSightToTarget && this.tryCloseSpecial(target, distance)) {
                return;
            }
            if (this.jumpBack != null && this.cooldowns.isReady(JUMP_BACK_ID)) {
                this.startAction(this.jumpBack);
                return;
            }
            if (this.sidestep != null && this.cooldowns.isReady(SIDESTEP_ID)) {
                this.startAction(this.sidestep);
                return;
            }
            if (this.cooldowns.isReady(STAFF_ID) && distance < 3.8F && this.canStrike(target, kit.staffSwing())) {
                this.startAction(kit.staffSwing());
                return;
            }
        }

        Vec3 exit = this.entity.position().add(this.locomotion.retreatVector(target).scale(8.0));
        this.locomotion.setFallbackGoal(exit, 1.3);
        this.locomotion.retreatFrom(target, SPACING_NEAR, profile.withMaxSpeed(profile.maxSpeed() * 1.2));
    }

    /**
     * Springwater (aqua) when an enemy is close or it or an ally nearby is below 60 %; stone skin (terra) when it or an
     * ally nearby was just attacked.
     */
    private boolean trySupport(float distance) {
        Kit kit = this.kit();
        if (this.variant() == ElementalVariant.AQUA && this.cooldowns.isReady(SPRING_ID)) {
            boolean wounded = healthPct(this.entity) < SPRING_BELOW || this.nearbyAllies.stream().anyMatch(ally -> healthPct(ally) < SPRING_BELOW);
            boolean healingAllowed = RenegadesConfig.ENABLE_PRIEST_HEALING.get();
            if (distance < SPRING_RADIUS || wounded && healingAllowed) {
                return this.startFullCooldown(kit.specialCast());
            }
        }
        if (this.variant() == ElementalVariant.TERRA && this.cooldowns.isReady(STONE_SKIN_ID)) {
            boolean attacked = recentlyAttacked(this.entity) || this.nearbyAllies.stream().anyMatch(ElementalWizardBrain::recentlyAttacked);
            boolean someoneBare = !this.entity.hasEffect(this.stoneFlesh) || this.nearbyAllies.stream().anyMatch(ally -> !ally.hasEffect(this.stoneFlesh));
            if (attacked && someoneBare) {
                return this.startFullCooldown(kit.specialCast());
            }
        }
        return false;
    }

    /**
     * Aeroblast (wind) against a target closer than 6 blocks.
     */
    private boolean tryCloseSpecial(LivingEntity target, float distance) {
        return this.variant() == ElementalVariant.WIND && distance < AEROBLAST_RANGE && this.cooldowns.isReady(AEROBLAST_ID)
                && this.startFullCooldown(this.kit().specialCast());
    }

    private boolean trySecond(LivingEntity target, float distance) {
        Kit kit = this.kit();
        if (!this.cooldowns.isReady(kit.secondCast().id())) {
            return false;
        }
        return switch (this.variant()) {
            case AQUA -> distance <= WHIP_RANGE && this.startAction(kit.secondCast());
            case TERRA -> distance <= IMPALE_RANGE && target.onGround() && this.startFullCooldown(kit.secondCast());
            case WIND -> distance <= WINDFIELD_RANGE && this.startFullCooldown(kit.secondCast());
        };
    }

    private boolean inMainRange(float distance) {
        return this.variant() != ElementalVariant.WIND || distance <= CUTTER_RANGE;
    }

    /**
     * Support and control spells keep their full cooldown: the usual shortening (RenegadeEntity#scaledCooldown, down to
     * a fifth) would let them heal, hold or throw the target almost without pause.
     */
    private boolean startFullCooldown(CombatAction action) {
        if (this.startAction(action)) {
            this.cooldowns.trigger(action.id(), action.cooldownTicks());
            return true;
        }
        return false;
    }

    private MovementProfile spacingProfile() {
        return this.currentStance == WizardBrain.Stance.ZONING ? MovementProfile.ELASTIC.urgent(1.12, 1.15, 1.05F) : MovementProfile.ELASTIC;
    }

    private static float healthPct(LivingEntity entity) {
        return entity.getHealth() / Math.max(1.0F, entity.getMaxHealth());
    }

    private static boolean recentlyAttacked(LivingEntity entity) {
        return entity.getLastHurtByMob() != null && entity.tickCount - entity.getLastHurtByMobTimestamp() < RECENTLY_HIT_TICKS;
    }

    private void spellWindup(AbilitySpec spec) {
        if (this.entity.level() instanceof ServerLevel level) {
            AbilityPresentation.spellWindup(level, this.entity, spec);
        }
    }

    private void shoot(AbilitySpec spec) {
        LivingEntity target = this.entity.getTarget();
        if (target != null && this.entity.level() instanceof ServerLevel level && this.entity.hasLineOfSight(target)) {
            this.entity.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            AbilityExecutor.cast(level, this.entity, spec, CastTarget.at(this.entity, target));
        }
    }

    /**
     * A direct spell (whip, air cutter, aeroblast) hits the target if it is still in range and in sight.
     */
    private void castDirect(AbilitySpec spec, float range) {
        LivingEntity target = this.entity.getTarget();
        if (target == null || !target.isAlive() || !(this.entity.level() instanceof ServerLevel level) || !this.entity.hasLineOfSight(target)
                || this.entity.distanceTo(target) > range) {
            return;
        }
        this.entity.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        AbilityExecutor.cast(level, this.entity, spec, CastTarget.at(this.entity, target));
    }

    private void lockSpot(AbilitySpec spec, @Nullable LivingEntity target) {
        this.lockedSpot = target == null ? null : target.position();
        if (this.lockedSpot != null && this.entity.level() instanceof ServerLevel level) {
            AbilityExecutor.windup(level, this.entity, spec, CastTarget.at(this.entity, this.lockedSpot));
        }
    }

    /**
     * Impale and windfield land where their windup marked the ground, wherever the target is by then.
     */
    private void castAtSpot(AbilitySpec spec) {
        Vec3 spot = this.lockedSpot;
        this.lockedSpot = null;
        if (spot != null && this.entity.level() instanceof ServerLevel level) {
            AbilityExecutor.cast(level, this.entity, spec, CastTarget.at(this.entity, spot));
        }
    }

    /**
     * Springwater: damage and soaked for enemies around it, healing and cleansing water for its allies there and
     * itself.
     */
    private void castSpringwater(AbilitySpec spring) {
        if (!(this.entity.level() instanceof ServerLevel level)) {
            return;
        }
        AbilityExecutor.cast(level, this.entity, spring, CastTarget.at(this.entity, this.entity.position()));
        ImpactContext self = ImpactContext.of(this.entity, spring.school(), this.entity.position(), null);
        for (Impact impact : spring.allyImpacts()) {
            impact.apply(self, this.entity);
        }
        if (!AbilityPresentation.spellImpact(level, this.entity, spring, this.entity).particles()) {
            MagicParticles.pulse(level, MagicSchool.WATER, this.entity.getBoundingBox().getCenter(), 1.2F);
        }
    }

    /**
     * A hit of 4+ damage or any melee hit during the springwater windup breaks it; its cooldown then runs at half length.
     */
    @Override
    public void onDamageTaken(DamageSource source, float amount) {
        super.onDamageTaken(source, amount);
        CombatAction current = this.actions.current();
        if (current == null || this.variant() != ElementalVariant.AQUA || current != this.kit().specialCast()
                || this.actions.phase() != AttackPhase.WINDUP) {
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
            Vec3 at = this.entity.position();
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, at.x, at.y + 1.4, at.z, 10, 0.3, 0.3, 0.3, 0.02);
            MagicParticles.sound(level, at, SoundEvents.FIRE_EXTINGUISH, 0.8F, 1.4F);
        }
    }

    @Override
    public void onTargetLost() {
        super.onTargetLost();
        this.lockedSpot = null;
    }

    public void recordHeal(float amount) {
        this.healedTotal += amount;
    }

    @Override
    public String describe() {
        ElementalVariant variant = this.variant();
        double power = MagicBackends.basePower(this.entity, variant.school());
        String spells = switch (variant) {
            case AQUA -> String.format(Locale.ROOT, "water ball %.1f x%d, whip %.1f, springwater %.1f (heal %.1f), healed %.1f HP, interrupted %d",
                    power * VOLLEY_DAMAGE, VOLLEY_SHOTS, power * WHIP_DAMAGE, power * SPRING_DAMAGE,
                    power * SPRING_HEAL * RenegadesConfig.HEALING_MULTIPLIER.get(), this.healedTotal, this.interruptions);
            case TERRA -> String.format(Locale.ROOT, "stone spear %.1f, impale %.1f", power * SPEAR_DAMAGE, power * IMPALE_DAMAGE);
            case WIND -> String.format(Locale.ROOT, "air cutter %.1f, aeroblast %.1f, windfield %.1f", power * CUTTER_DAMAGE,
                    power * AEROBLAST_DAMAGE, power * WINDFIELD_DAMAGE);
        };
        return super.describe() + String.format(Locale.ROOT, ", variant %s, backend %s, power %.1f, %s", variant.id(), MagicBackends.active().name(),
                power, spells);
    }
}
