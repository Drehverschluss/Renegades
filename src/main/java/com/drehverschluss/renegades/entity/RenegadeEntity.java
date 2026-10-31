package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.combat.CombatProfiler;
import com.drehverschluss.renegades.combat.MovementOwner;
import com.drehverschluss.renegades.combat.brain.MobBrain;
import com.drehverschluss.renegades.combat.brain.MobBrainGoal;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.ai.FollowGroupLeaderGoal;
import com.drehverschluss.renegades.equipment.EquipmentSelector;
import com.drehverschluss.renegades.group.GroupSpawner;
import com.drehverschluss.renegades.group.RenegadeGroups;
import com.drehverschluss.renegades.group.SpawnGroupTemplate;
import com.drehverschluss.renegades.group.SpawnGroups;
import com.drehverschluss.renegades.group.SpawnStats;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Class-neutral base for all renegades. Equipment comes from the data-driven equipment pools; concrete classes
 * (warrior, rogue, ...) add their combat goals and animations.
 */
public abstract class RenegadeEntity extends Monster implements GeoEntity {
    /**
     * GeckoLib controller for the triggered combat animations (attacks, dashes, parries).
     */
    public static final String ACTION_CONTROLLER = "action";
    private static final String TAG_GROUP = "RenegadesGroup";
    private static final double COOLDOWN_ALLY_RADIUS = 32.0D;
    private static final int MIN_SCALED_COOLDOWN = 20;
    private static final int MAX_FALL_DISTANCE = 3;
    private static final EquipmentSlot[] DROPPABLE_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID groupId;
    // Group rolled on a natural spawn; its members are spawned on the first tick, once the leader is in the world.
    @Nullable
    private SpawnGroupTemplate pendingGroup;
    // Whether the pending group comes from a real natural spawn (counted in SpawnStats) rather than a command.
    private boolean countGroupInStats;
    // Set on spawn: worn gear can raise max health (e.g. Paladins armor), but vanilla applies equipment attributes only
    // in the first tick, after finalizeSpawn already set the health.
    private boolean fillHealthAfterFirstTick;
    // Server only: created in registerGoals, which the Mob constructor calls only on the server.
    @Nullable
    private MobBrain brain;
    private boolean performing;
    // Client only: locomotion animation state while in combat (ported from ANARCHY's MinibossEntity#animTick).
    private CombatMoveState combatMoveState = CombatMoveState.STANCE;
    private CombatMoveState combatMoveCandidate = CombatMoveState.STANCE;
    private int combatMoveCandidateTicks;
    private float combatMoveSpeed = 1.0F;
    private float smoothedSpeed;
    // Client only: walking (out of combat) with hysteresis, see updateWalkState.
    private boolean walking;

    /**
     * Out-of-combat animations shared by all classes: a calm stand and a plain walk with swinging arms.
     */
    protected static final RawAnimation WALK = RawAnimation.begin().thenLoop("walking");
    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    // Horizontal speed (blocks per tick, smoothed) from which a renegade counts as walking, and below which it stops.
    private static final float WALK_START_SPEED = 0.02F;
    private static final float WALK_STOP_SPEED = 0.01F;
    // Speed at which "walking" (one step every second) plays at its normal tempo; the tempo follows the real speed
    // within 0.6-2.0, so wandering (about 0.1 blocks per tick) gets about one step every half second.
    private static final float WALK_REFERENCE_SPEED = 0.05F;
    // Range of the per-renegade start phase of the idle and walk loops, in ticks.
    private static final int ANIMATION_PHASE_TICKS = 40;

    /**
     * Movement animation while in combat: standing in stance, advancing, strafing or backing off.
     */
    public enum CombatMoveState {
        STANCE, ADVANCE, LATERAL_LEFT, LATERAL_RIGHT, BACKSTEP;

        boolean isDirectional() {
            return this != STANCE;
        }
    }

    protected RenegadeEntity(EntityType<? extends RenegadeEntity> entityType, Level level) {
        super(entityType, level);

        // Equipment drops are handled in dropCustomDeathLoot according to the server config.
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.brain = this.createBrain();
        if (this.brain != null) {
            this.goalSelector.addGoal(2, new MobBrainGoal(this, this.brain));
        }
        this.goalSelector.addGoal(4, new FollowGroupLeaderGoal(this, 1.0D, 12.0D, 6.0D));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /**
     * The class's combat brain; it runs while the renegade has a target. Called from registerGoals (server only),
     * before subclass fields are initialized, so it must not rely on them.
     */
    @Nullable
    protected MobBrain createBrain() {
        return null;
    }

    @Nullable
    public MobBrain getCombatBrain() {
        return this.brain;
    }

    public boolean isPerforming() {
        return this.performing;
    }

    public void setPerforming(boolean performing) {
        this.performing = performing;
    }

    /**
     * A combat cooldown, shortened like in ANARCHY (MinibossEntity#getCooldownCoeff): cooldown = base - base x strength x
     * coefficient, with coefficient = max(1, renegades^0.585) + 0.5 x gaussian noise, where renegades counts this one and
     * the renegades within 32 blocks while it has a target. Alone (coefficient ~1) this stays exactly as in ANARCHY (with
     * strength 0.4 about 60 % of the base on average). The extra shortening by nearby renegades is limited: at most 25 %
     * below the solo value of the same roll, and never below 20 ticks (or the base, if that is shorter);
     * cooldownGroupScaling = false drops the group part (always the solo value).
     *
     * @param strength how strongly the coefficient shortens the base (0.4 for actions)
     * @param floor    lowest cooldown of the original formula (e.g. 160 for the warrior's parry)
     */
    public int scaledCooldown(int base, double strength, int floor) {
        if (base <= 0) {
            return base;
        }
        double noise = 0.5D * this.getRandom().nextGaussian();
        int lowest = Math.max(1, floor);
        int solo = Math.max(lowest, base - (int) (base * Math.max(0.1D, 1.0D + noise) * strength));
        int result = solo;
        if (RenegadesConfig.COOLDOWN_GROUP_SCALING.get()) {
            double group = Math.max(0.1D, Math.max(1.0D, Math.pow(this.renegadesFightingNearby(), 0.58496250072D)) + noise);
            int grouped = Math.max(lowest, base - (int) (base * group * strength));
            result = Math.max(grouped, (int) Math.ceil(solo * 0.75D));
        }
        return Math.max(result, Math.min(base, MIN_SCALED_COOLDOWN));
    }

    /**
     * This renegade plus the renegades within 32 blocks, counted only while it has a target (1 otherwise).
     */
    private int renegadesFightingNearby() {
        int renegades = 1;
        if (!this.level().isClientSide() && this.getTarget() != null) {
            renegades += this.level().getEntitiesOfClass(RenegadeEntity.class, this.getBoundingBox().inflate(COOLDOWN_ALLY_RADIUS),
                    other -> other != this && other.distanceTo(this) < COOLDOWN_ALLY_RADIUS).size();
        }
        return renegades;
    }

    /**
     * Whether a swing of this renegade may hit the entity: its current target, players, and mobs that fight this
     * renegade. Never allies, other renegades (unless targeted) or bystanders such as villagers and animals.
     */
    public boolean canHarm(Entity candidate) {
        if (candidate == this || !(candidate instanceof LivingEntity living) || !living.isAlive() || candidate.isSpectator()
                || this.isAlliedTo(candidate)) {
            return false;
        }
        if (candidate == this.getTarget()) {
            return true;
        }
        if (candidate instanceof RenegadeEntity) {
            return false;
        }
        if (candidate instanceof Player player) {
            return !player.isCreative();
        }
        return candidate instanceof Mob mob && mob.getTarget() == this;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return this.meleeHit(target, 1.0F);
    }

    /**
     * A regular mob melee hit like vanilla's Mob#doHurtTarget (mob attack damage source, so shields can block it;
     * attack damage attribute incl. weapon, multipliers and level bonuses; weapon enchantments such as Sharpness,
     * Fire Aspect and Knockback; post-attack effects), with the base damage scaled by the given factor.
     */
    public boolean meleeHit(Entity target, float damageScale) {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * damageScale;
        DamageSource source = this.damageSources().mobAttack(this);
        if (this.level() instanceof ServerLevel serverLevel) {
            damage = EnchantmentHelper.modifyDamage(serverLevel, this.getWeaponItem(), target, source, damage);
        }

        boolean hurt = target.hurt(source, damage);
        if (hurt) {
            float knockback = this.getKnockback(target, source);
            if (knockback > 0.0F && target instanceof LivingEntity living) {
                living.knockback(knockback * 0.5F, Mth.sin(this.getYRot() * Mth.DEG_TO_RAD), -Mth.cos(this.getYRot() * Mth.DEG_TO_RAD));
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
            }
            if (this.level() instanceof ServerLevel serverLevel) {
                EnchantmentHelper.doPostAttackEffects(serverLevel, target, source);
            }
            this.setLastHurtMob(target);
            this.playAttackSound();
        }
        return hurt;
    }

    /**
     * Starts a triggered combat animation; GeckoLib syncs it to all tracking clients. A running triggered animation is
     * stopped first so the same animation can restart.
     */
    public void playActionAnimation(String controller, String animation) {
        this.stopTriggeredAnim(controller, null);
        this.triggerAnim(controller, animation);
    }

    public void stopActionAnimation(String controller, String animation) {
        this.stopTriggeredAnim(controller, animation);
    }

    @Override
    public void knockback(double strength, double x, double z) {
        super.knockback(strength, x, z);
        if (strength > 0.0D && this.brain != null) {
            this.brain.onKnockback();
        }
    }

    /**
     * While the brain steers (and on clients while in combat), the body faces where the renegade looks, so strafing
     * and backing off keep the body towards the target instead of turning it into the walking direction.
     */
    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        boolean steering = this.brain != null && this.brain.locomotion.owner() != MovementOwner.NAVIGATION;
        if (steering || this.level().isClientSide() && this.isAggressive()) {
            this.yBodyRot = this.level().isClientSide() ? this.yHeadRot : this.getYRot();
            return animStep;
        }
        return super.tickHeadTurn(yRot, animStep);
    }

    public CombatMoveState getCombatMoveState() {
        return this.combatMoveState;
    }

    public float getCombatMoveSpeed() {
        return this.combatMoveSpeed;
    }

    /**
     * Client: whether the renegade walks, from its own smoothed position change instead of GeckoLib's isMoving() (which
     * needs a client-side delta movement that slowly wandering mobs rarely have). Starts above 0.02 and stops below 0.01
     * blocks per tick, so it does not flicker between idle and walk.
     */
    private void updateWalkState() {
        if (this.walking ? this.smoothedSpeed < WALK_STOP_SPEED : this.smoothedSpeed > WALK_START_SPEED) {
            this.walking = !this.walking;
        }
    }

    public boolean isWalking() {
        return this.walking;
    }

    /**
     * Tempo of the movement controller: in combat the combat movement speed (unchanged), out of combat the walk tempo
     * coupled to the real speed (0.6-2.0 x) and a small fixed tempo difference per renegade, so they do not move in step.
     */
    public double movementAnimationSpeed() {
        if (this.isAggressive()) {
            return this.combatMoveState != CombatMoveState.STANCE ? this.combatMoveSpeed : 1.0D;
        }
        double tempo = 0.95D + 0.1D * ((this.getUUID().getMostSignificantBits() >>> 1) % 1000L) / 1000.0D;
        return this.walking ? Mth.clamp(this.smoothedSpeed / WALK_REFERENCE_SPEED, 0.6D, 2.0D) * tempo : tempo;
    }

    /**
     * Fixed start phase (from the UUID, stable across reloads) of the idle and walk loops; see
     * MovementAnimationController.
     */
    public double animationPhaseTicks() {
        return Math.floorMod(this.getUUID().getLeastSignificantBits(), ANIMATION_PHASE_TICKS);
    }

    /**
     * Client: picks the combat movement animation from the actual movement relative to the body, with a two-tick
     * hysteresis between directions (ANARCHY's MinibossEntity#animTick).
     */
    private void updateCombatMoveState() {
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        float horizontalSpeed = (float) Math.sqrt(dx * dx + dz * dz);
        float alpha = horizontalSpeed > this.smoothedSpeed ? 0.6F : 0.2F;
        this.smoothedSpeed = Mth.lerp(alpha, this.smoothedSpeed, horizontalSpeed);
        this.updateWalkState();
        boolean moving = this.smoothedSpeed > 0.012F;
        CombatMoveState newState = moving ? this.combatDirection(dx, dz, horizontalSpeed) : CombatMoveState.STANCE;
        this.combatMoveSpeed = Mth.clamp(this.smoothedSpeed / 0.16F, 0.55F, 1.9F);

        if (newState != this.combatMoveState && newState.isDirectional() && this.combatMoveState.isDirectional()) {
            if (newState == this.combatMoveCandidate) {
                this.combatMoveCandidateTicks++;
            } else {
                this.combatMoveCandidate = newState;
                this.combatMoveCandidateTicks = 1;
            }
            if (this.combatMoveCandidateTicks < 2) {
                return;
            }
        }
        this.combatMoveCandidate = newState;
        this.combatMoveState = newState;
    }

    private CombatMoveState combatDirection(double dx, double dz, float speed) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        Vec3 right = Vec3.directionFromRotation(0.0F, this.yBodyRot + 90.0F);
        double along = dx * forward.x + dz * forward.z;
        double lateral = dx * right.x + dz * right.z;
        if (Math.abs(lateral) > Math.abs(along) * 1.25D) {
            return lateral > 0.0D ? CombatMoveState.LATERAL_RIGHT : CombatMoveState.LATERAL_LEFT;
        }
        return along < -speed * 0.2D ? CombatMoveState.BACKSTEP : CombatMoveState.ADVANCE;
    }

    /**
     * Called after the equipment was rolled from a pool entry; classes with variants (e.g. the wizard's school) take
     * the entry's variant, or pick one at random if it is null or unknown.
     */
    public void applyPoolVariant(@Nullable String variant) {
    }

    /**
     * Never more than 3 blocks, also while chasing a target (vanilla allows much deeper drops then), matching the
     * hazard check of the direct combat movement.
     */
    @Override
    public int getMaxFallDistance() {
        return MAX_FALL_DISTANCE;
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        // Animations and the held item layer are authored for the right hand.
        this.setLeftHanded(false);
        // Before the equipment roll: it commits the Dynamic Difficulty level, whose ADD_MULTIPLIED_BASE bonuses then
        // scale the multiplied base values. Only runs for new spawns, never when loading from NBT.
        this.applyStrengthMultipliers();
        EquipmentSelector.equipOnSpawn(this, level.getLevel());
        this.setHealth(this.getMaxHealth());
        this.fillHealthAfterFirstTick = true;

        // Group members already carry their group id and never roll a group of their own.
        if (spawnType == MobSpawnType.NATURAL && this.groupId == null && this.pendingGroup == null) {
            this.pendingGroup = SpawnGroups.pick(this, level.getLevel());
            this.countGroupInStats = true;
        }
        if (spawnType == MobSpawnType.NATURAL) {
            Renegades.LOGGER.debug("Natural spawn of {} at {} (group template: {})", this.getType().getDescriptionId(),
                    this.blockPosition(), this.pendingGroup == null ? "none" : this.pendingGroup.id());
        }
        return data;
    }

    /**
     * Spawn placement rule: solid ground, no fluids, not on Peaceful; light only matters with requireDarkness.
     */
    public static boolean checkRenegadeSpawnRules(EntityType<? extends RenegadeEntity> type, ServerLevelAccessor level, MobSpawnType spawnType,
                                                  BlockPos pos, RandomSource random) {
        return RenegadesConfig.REQUIRE_DARKNESS.get()
                ? Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random)
                : Monster.checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    /**
     * Light: PathfinderMob's check rejects bright spots for monsters (negative walk target value); it only applies
     * with requireDarkness. Rarity and the maxNearby cap are handled by RenegadeSpawner.
     */
    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType spawnType) {
        return !RenegadesConfig.REQUIRE_DARKNESS.get() || super.checkSpawnRules(level, spawnType);
    }

    private void applyStrengthMultipliers() {
        multiplyBaseValue(Attributes.MAX_HEALTH, RenegadesConfig.HEALTH_MULTIPLIER.get());
        multiplyBaseValue(Attributes.ATTACK_DAMAGE, RenegadesConfig.DAMAGE_MULTIPLIER.get());
    }

    private void multiplyBaseValue(Holder<Attribute> attribute, double multiplier) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null && multiplier != 1.0D) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
        }
    }

    /**
     * Keeps renegades from natural spawns, spawn eggs and commands. Deliberately not done through the
     * persistenceRequired flag, which Let Me Despawn manages on its own. The spawn type is recorded by NeoForge in
     * Mob#finalizeSpawn and saved as "neoforge:spawn_type"; it is null if finalizeSpawn never ran (/summon with NBT).
     */
    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.isPersistentBySpawnType();
    }

    private boolean isPersistentBySpawnType() {
        MobSpawnType origin = this.getSpawnType();
        if (this.level().isClientSide || origin == MobSpawnType.SPAWNER || origin == MobSpawnType.TRIAL_SPAWNER) {
            return false;
        }
        return RenegadesConfig.PERSISTENT_NATURAL_SPAWNS.get();
    }

    @Override
    public void tick() {
        long start = CombatProfiler.start();
        super.tick();
        if (this.level().isClientSide) {
            this.updateCombatMoveState();
        } else {
            if (this.fillHealthAfterFirstTick) {
                this.fillHealthAfterFirstTick = false;
                if (this.getHealth() < this.getMaxHealth()) {
                    this.setHealth(this.getMaxHealth());
                }
            }
            if (this.pendingGroup != null) {
                this.spawnPendingGroup();
            }
        }
        CombatProfiler.end(CombatProfiler.Section.ENTITY_TICK, start);
    }

    /**
     * Spawns the members of the group rolled in finalizeSpawn (or set by the spawngroup command).
     *
     * @return number of spawned members
     */
    public int spawnPendingGroup() {
        SpawnGroupTemplate template = this.pendingGroup;
        this.pendingGroup = null;
        int members = template == null ? 0 : GroupSpawner.spawnMembers(this, template, this.countGroupInStats);
        if (this.countGroupInStats) {
            SpawnStats.recordMembers(members);
            this.countGroupInStats = false;
        }
        return members;
    }

    /**
     * Forces a group template; must be called before finalizeSpawn so no random group is rolled.
     */
    public void setPendingGroup(SpawnGroupTemplate template) {
        this.pendingGroup = template;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide) {
            if (this.brain != null) {
                this.brain.onDamageTaken(source, amount);
            }
            if (this.groupId != null && source.getEntity() instanceof Player player) {
                RenegadeGroups.alertGroup(this, player);
            }
            if (source.getEntity() instanceof RenegadeEntity attacker && attacker != this) {
                // Should never happen (renegades never harm each other unless one targets the other); debug aid.
                Renegades.LOGGER.debug("Renegade {} was hit by renegade {} ({}, {} damage)", this, attacker, source.getMsgId(), amount);
            }
        }
        return hurt;
    }

    @Nullable
    public UUID getGroupId() {
        return this.groupId;
    }

    public void setGroupId(@Nullable UUID groupId) {
        this.groupId = groupId;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.groupId != null) {
            tag.putUUID(TAG_GROUP, this.groupId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.groupId = tag.hasUUID(TAG_GROUP) ? tag.getUUID(TAG_GROUP) : null;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);

        if (RenegadesConfig.REQUIRE_PLAYER_KILL.get() && !recentlyHit) {
            return;
        }

        for (EquipmentSlot slot : DROPPABLE_SLOTS) {
            ItemStack stack = this.getItemBySlot(slot);
            if (stack.isEmpty() || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                continue;
            }

            float chance = (slot.getType() == EquipmentSlot.Type.HAND
                    ? RenegadesConfig.WEAPON_DROP_CHANCE
                    : RenegadesConfig.ARMOR_DROP_CHANCE).get().floatValue();
            if (chance <= 0.0F) {
                continue;
            }
            // Same hook as vanilla: lets Looting-like enchantments and GetEquipmentDropChanceEvent adjust the chance.
            if (damageSource.getEntity() instanceof LivingEntity killer) {
                chance = EnchantmentHelper.processEquipmentDropChance(level, killer, damageSource, chance, slot);
            }
            if (this.random.nextFloat() >= chance) {
                continue;
            }

            if (stack.isDamageableItem()) {
                // Random remaining durability between dropDurabilityMin and dropDurabilityMax, never 0. Wear from the
                // fight (e.g. a shield that blocked) does not count, only this roll.
                double min = RenegadesConfig.DROP_DURABILITY_MIN.get();
                double max = RenegadesConfig.DROP_DURABILITY_MAX.get();
                double fraction = Math.min(min, max) + this.random.nextDouble() * Math.abs(max - min);
                int remaining = Mth.clamp((int) Math.round(stack.getMaxDamage() * fraction), 1, stack.getMaxDamage());
                stack.setDamageValue(stack.getMaxDamage() - remaining);
            }
            this.spawnAtLocation(stack);
            this.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
