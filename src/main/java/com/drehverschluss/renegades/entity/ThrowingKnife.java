package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.registry.ModEntityTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The rogue's thrown knife (replaces ANARCHY's "rogue/knife" ability): flies straight without gravity, deals a mob
 * projectile hit (shields block it) and vanishes on impact or after its lifetime. It only shows the weapon; no item is
 * ever lost or dropped.
 */
public class ThrowingKnife extends ThrowableItemProjectile {
    public static final float SPEED = 0.8F;
    public static final int LIFETIME = 40;
    // Share of the thrower's attack damage (lower than ANARCHY's 1.2, since the attack value includes weapon and level).
    public static final float DAMAGE_FACTOR = 0.6F;
    /**
     * Daggers are shown as they are while flying (main hand first, then off hand); otherwise an iron sword is shown.
     */
    public static final TagKey<Item> DAGGERS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("rpg_series", "weapon_type/dagger"));
    private static final String TAG_DAMAGE = "Damage";

    private float damage;

    public ThrowingKnife(EntityType<? extends ThrowingKnife> type, Level level) {
        super(type, level);
    }

    public ThrowingKnife(RenegadeEntity thrower, Level level) {
        super(ModEntityTypes.THROWING_KNIFE.get(), thrower, level);
        this.damage = (float) thrower.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE_FACTOR;
        ItemStack weapon = thrower.getMainHandItem().is(DAGGERS) ? thrower.getMainHandItem() : thrower.getOffhandItem();
        this.setItem(weapon.is(DAGGERS) ? weapon.copyWithCount(1) : new ItemStack(Items.IRON_SWORD));
    }

    /**
     * Throws a knife from the thrower's eyes straight at the centre of the target.
     */
    public static void throwAt(RenegadeEntity thrower, LivingEntity target) {
        ThrowingKnife knife = new ThrowingKnife(thrower, thrower.level());
        Vec3 from = knife.position();
        Vec3 to = target.getBoundingBox().getCenter();
        knife.shoot(to.x - from.x, to.y - from.y, to.z - from.z, SPEED, 0.0F);
        thrower.level().addFreshEntity(knife);
        thrower.playSound(SoundEvents.TRIDENT_THROW.value(), 0.8F, 1.4F + thrower.getRandom().nextFloat() * 0.2F);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_SWORD;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount > LIFETIME) {
            this.discard();
        }
    }

    /**
     * Same target rules as a melee swing: the knife flies through allies, bystanders and creative players.
     */
    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && (!(this.getOwner() instanceof RenegadeEntity thrower) || thrower.canHarm(target));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        LivingEntity thrower = this.getOwner() instanceof LivingEntity living ? living : null;
        if (target.hurt(this.damageSources().mobProjectile(this, thrower), this.damage)) {
            this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.2F);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.playSound(SoundEvents.TRIDENT_HIT_GROUND, 0.8F, 1.3F);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide()) {
            this.discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat(TAG_DAMAGE, this.damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat(TAG_DAMAGE);
    }
}
