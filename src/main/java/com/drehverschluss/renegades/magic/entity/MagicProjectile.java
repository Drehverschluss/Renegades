// Based on BossProjectile from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: client look is a particle trail in the school's colours instead of AtlasVFX; never saved; hits only what
// the caster may harm and stops (bursting there) at the first block.
package com.drehverschluss.renegades.magic.entity;

import com.drehverschluss.renegades.magic.AbilityExecutor;
import com.drehverschluss.renegades.magic.AbilityPresentation;
import com.drehverschluss.renegades.magic.AbilitySpec;
import com.drehverschluss.renegades.magic.CastTarget;
import com.drehverschluss.renegades.magic.Delivery;
import com.drehverschluss.renegades.magic.ImpactContext;
import com.drehverschluss.renegades.magic.MagicSchool;
import com.drehverschluss.renegades.registry.ModEntityTypes;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A spell projectile: flies straight (optionally homing or falling), applies its spell to the first target it may
 * harm (or several with pierce) and bursts at the end. It never damages blocks, sets no block on fire and drops
 * nothing.
 */
public class MagicProjectile extends Entity implements TraceableEntity {
    private static final EntityDataAccessor<Byte> SCHOOL = SynchedEntityData.defineId(MagicProjectile.class, EntityDataSerializers.BYTE);

    @Nullable
    private AbilitySpec ability;
    @Nullable
    private Delivery.ProjectileSpec spec;
    @Nullable
    private ImpactContext context;
    @Nullable
    private Entity homingTarget;
    private boolean meteor;
    @Nullable
    private Consumer<LivingEntity> onHit;
    private final Set<Entity> struck = new HashSet<>();

    public MagicProjectile(EntityType<? extends MagicProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public static MagicProjectile launch(ServerLevel level, LivingEntity caster, AbilitySpec ability, Delivery.ProjectileSpec spec, ImpactContext context,
                                         Vec3 from, Vec3 direction, @Nullable Entity homingTarget, boolean meteor) {
        MagicProjectile projectile = new MagicProjectile(ModEntityTypes.MAGIC_PROJECTILE.get(), level);
        projectile.ability = ability;
        projectile.spec = spec;
        projectile.context = context;
        projectile.homingTarget = spec.homingDegrees() > 0.0F ? homingTarget : null;
        projectile.meteor = meteor;
        projectile.getEntityData().set(SCHOOL, (byte) ability.lookSchool().ordinal());
        projectile.setPos(from);
        Vec3 heading = direction.lengthSqr() < 1.0E-8 ? caster.getLookAngle() : direction.normalize();
        projectile.setDeltaMovement(heading.scale(spec.speed()));
        ProjectileUtil.rotateTowardsMovement(projectile, 1.0F);
        level.addFreshEntity(projectile);
        return projectile;
    }

    public MagicProjectile onHit(Consumer<LivingEntity> listener) {
        this.onHit = listener;
        return this;
    }

    @Nullable
    @Override
    public Entity getOwner() {
        return this.context == null ? null : this.context.caster();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SCHOOL, (byte) 0);
    }

    public MagicSchool school() {
        MagicSchool[] schools = MagicSchool.values();
        return schools[Math.floorMod(this.getEntityData().get(SCHOOL), schools.length)];
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        boolean server = !this.level().isClientSide();
        if (!server) {
            this.spawnTrail();
        } else {
            if (this.ability == null || this.spec == null || this.context == null || this.tickCount > this.spec.lifetimeTicks()) {
                this.discard();
                return;
            }
            this.steer();
        }

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS && server) {
            if (hit instanceof EntityHitResult entityHit) {
                this.onEntityHit(entityHit);
            } else if (hit instanceof BlockHitResult blockHit) {
                this.onBlockHit(blockHit);
            }
            if (this.isRemoved()) {
                return;
            }
        }

        Vec3 velocity = this.getDeltaMovement();
        if (this.spec != null && this.spec.gravity() != 0.0) {
            velocity = velocity.add(0.0, -this.spec.gravity(), 0.0);
            this.setDeltaMovement(velocity);
        }
        this.setPos(this.position().add(velocity));
        ProjectileUtil.rotateTowardsMovement(this, 0.5F);
    }

    private void spawnTrail() {
        MagicSchool school = this.school();
        Vec3 velocity = this.getDeltaMovement();
        for (int i = 0; i < 3; i++) {
            double t = i / 3.0;
            double x = this.getX() - velocity.x * t;
            double y = this.getY() - velocity.y * t;
            double z = this.getZ() - velocity.z * t;
            this.level().addParticle(school.particle(), x, y, z, 0.0, 0.0, 0.0);
            this.level().addParticle(school.dust(1.4F), x + (this.random.nextDouble() - 0.5) * 0.15, y + (this.random.nextDouble() - 0.5) * 0.15,
                    z + (this.random.nextDouble() - 0.5) * 0.15, 0.0, 0.0, 0.0);
        }
    }

    private void steer() {
        Entity target = this.homingTarget;
        if (target == null || !target.isAlive() || this.spec == null || this.tickCount < this.spec.homingDelay()
                || this.spec.homingEnd() > 0 && this.tickCount >= this.spec.homingEnd()) {
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0E-6) {
            return;
        }
        Vec3 current = velocity.scale(1.0 / speed);
        Vec3 wanted = target.getBoundingBox().getCenter().subtract(this.position()).normalize();
        double angle = Math.acos(Mth.clamp(current.dot(wanted), -1.0, 1.0));
        double max = Math.toRadians(this.spec.homingDegrees());
        Vec3 next;
        if (angle <= max || angle < 1.0E-6) {
            next = wanted;
        } else {
            double t = max / angle;
            double sinAngle = Math.sin(angle);
            next = current.scale(Math.sin((1.0 - t) * angle) / sinAngle).add(wanted.scale(Math.sin(t * angle) / sinAngle));
        }
        this.setDeltaMovement(next.normalize().scale(speed));
        this.hurtMarked = true;
    }

    private boolean canHit(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity.isSpectator() || entity == this.getOwner() || this.struck.contains(entity)) {
            return false;
        }
        return this.ability != null && this.context != null && AbilityExecutor.reaches(this.ability, this.context.caster(), entity);
    }

    private void onEntityHit(EntityHitResult hitResult) {
        if (!(this.level() instanceof ServerLevel level) || this.ability == null || this.spec == null || this.context == null
                || !(hitResult.getEntity() instanceof LivingEntity target)) {
            return;
        }
        this.struck.add(target);
        if (this.onHit != null) {
            this.onHit.accept(target);
        }
        AbilityExecutor.applyAll(this.ability, this.context.withOrigin(this.position()).withDirect(this), target);
        if (this.struck.size() > this.spec.pierce()) {
            this.land(level, target);
        } else {
            AbilityPresentation.impact(level, this.ability, this.position(), 0.0F);
        }
    }

    private void onBlockHit(BlockHitResult hitResult) {
        if (this.level() instanceof ServerLevel level) {
            // Stays on this side of the block, so the burst does not reach through it.
            Vec3 back = this.getDeltaMovement().lengthSqr() > 1.0E-6 ? this.getDeltaMovement().normalize().scale(0.2) : Vec3.ZERO;
            this.setPos(hitResult.getLocation().subtract(back));
            this.land(level, null);
        }
    }

    private void land(ServerLevel level, @Nullable Entity alreadyStruck) {
        if (this.ability != null && this.spec != null) {
            float radius = this.spec.burst() == null ? 0.0F : (float) this.spec.burst().shape().extent();
            AbilityPresentation.impact(level, this.ability, this.position(), radius);
        }
        if (this.spec != null && this.spec.burst() != null && this.ability != null && this.context != null) {
            AbilityExecutor.applyArea(level, this.ability, this.context.withDirect(this), this.spec.burst(), this.position(),
                    CastTarget.yawOf(this.getDeltaMovement()), alreadyStruck);
        }
        this.discard();
    }

    public boolean isMeteor() {
        return this.meteor;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
