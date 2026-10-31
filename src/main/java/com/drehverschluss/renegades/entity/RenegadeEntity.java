package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.Renegades;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Class-neutral base for all renegades. Equipment comes from the data-driven equipment pools; concrete classes
 * (warrior, rogue, ...) add their combat goals and animations.
 */
public abstract class RenegadeEntity extends Monster implements GeoEntity {
    private static final String TAG_GROUP = "RenegadesGroup";
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
        this.goalSelector.addGoal(4, new FollowGroupLeaderGoal(this, 1.0D, 12.0D, 6.0D));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
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
        super.tick();
        if (this.pendingGroup != null && !this.level().isClientSide) {
            this.spawnPendingGroup();
        }
    }

    /**
     * Spawns the members of the group rolled in finalizeSpawn (or set by the spawngroup command).
     *
     * @return number of spawned members
     */
    public int spawnPendingGroup() {
        SpawnGroupTemplate template = this.pendingGroup;
        this.pendingGroup = null;
        int members = template == null ? 0 : GroupSpawner.spawnMembers(this, template);
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
        if (hurt && !this.level().isClientSide && this.groupId != null && source.getEntity() instanceof Player player) {
            RenegadeGroups.alertGroup(this, player);
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

            if (!RenegadesConfig.DROP_UNDAMAGED.get() && stack.isDamageableItem()) {
                // Vanilla's random durability loss for mob equipment.
                stack.setDamageValue(stack.getMaxDamage() - this.random.nextInt(1 + this.random.nextInt(Math.max(stack.getMaxDamage() - 3, 1))));
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
