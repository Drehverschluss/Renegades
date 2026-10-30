package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.equipment.EquipmentSelector;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
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
import net.minecraft.world.level.ServerLevelAccessor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Class-neutral base for all renegades. Equipment comes from the data-driven equipment pools; concrete classes
 * (warrior, rogue, ...) add their combat goals and animations.
 */
public abstract class RenegadeEntity extends Monster implements GeoEntity {
    private static final EquipmentSlot[] DROPPABLE_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

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
        EquipmentSelector.equipOnSpawn(this, level.getLevel());
        return data;
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
