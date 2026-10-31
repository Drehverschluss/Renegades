package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.combat.AreaStrike;
import com.drehverschluss.renegades.registry.ModEntityTypes;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The archer's thrown trap (replaces ANARCHY's TrapCleann and its "mercenary/trap_blast" ability). It lands, arms
 * after 2 seconds and then goes off as soon as someone the archer may harm comes within 2 blocks: a cylindrical blast
 * (radius 2.5, height 3) dealing the archer's attack damage x 1.5. It vanishes after 8 seconds, when the archer dies
 * or is gone, and is never saved (gone after a chunk unload or restart). It drops nothing.
 */
public class ArcherTrap extends Entity implements GeoEntity {
    public static final int ARM_TICKS = 40;
    public static final int LIFETIME_TICKS = 160;
    public static final double TRIGGER_RADIUS = 2.0;
    public static final double BLAST_RADIUS = 2.5;
    public static final double BLAST_HEIGHT = 3.0;
    public static final float BLAST_DAMAGE = 1.5F;
    public static final double BLAST_KNOCKBACK = 0.4;
    private static final double GRAVITY = 0.05;
    private static final RawAnimation DROP = RawAnimation.begin().thenPlayAndHold("animation.model.new");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private RenegadeEntity owner;

    public ArcherTrap(EntityType<? extends ArcherTrap> type, Level level) {
        super(type, level);
    }

    public ArcherTrap(RenegadeEntity owner, Vec3 position, Vec3 velocity) {
        this(ModEntityTypes.ARCHER_TRAP.get(), owner.level());
        this.owner = owner;
        this.setPos(position);
        this.setDeltaMovement(velocity);
        this.setYRot(owner.getYRot());
        this.yRotO = this.getYRot();
    }

    @Nullable
    public RenegadeEntity getOwner() {
        return this.owner;
    }

    public boolean isArmed() {
        return this.tickCount > ARM_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -GRAVITY, 0.0));
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.onGround() ? Vec3.ZERO : this.getDeltaMovement().scale(0.98));

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.owner == null || !this.owner.isAlive() || this.owner.isRemoved() || this.tickCount > LIFETIME_TICKS) {
            this.discard();
            return;
        }
        if (this.isArmed() && !AreaStrike.victims(this.owner, this.position(), TRIGGER_RADIUS, 2.0).isEmpty()) {
            this.explode(level);
        }
    }

    private void explode(ServerLevel level) {
        RenegadeEntity attacker = this.owner;
        if (attacker == null) {
            return;
        }
        Vec3 centre = this.position();
        float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * BLAST_DAMAGE;
        DamageSource source = this.damageSources().mobProjectile(this, attacker);
        for (LivingEntity victim : AreaStrike.victims(attacker, centre, BLAST_RADIUS, BLAST_HEIGHT)) {
            // Like ANARCHY's trap blast: not held back by the victim's invulnerability ticks.
            victim.invulnerableTime = 0;
            if (victim.hurt(source, damage)) {
                AreaStrike.knockAway(victim, centre, BLAST_KNOCKBACK);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.3, centre.z, 2, 0.5, 0.2, 0.5, 0.0);
        level.sendParticles(ParticleTypes.FLAME, centre.x, centre.y + 0.3, centre.z, 30, BLAST_RADIUS * 0.4, 0.3, BLAST_RADIUS * 0.4, 0.05);
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.7F, 1.4F);
        this.discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "drop", 3, state -> state.setAndContinue(DROP)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
