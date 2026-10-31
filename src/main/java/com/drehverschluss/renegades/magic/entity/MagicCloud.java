// Based on BossCloud from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: client look is drifting particles in the school's colours instead of AtlasVFX; never saved.
package com.drehverschluss.renegades.magic.entity;

import com.drehverschluss.renegades.magic.AbilityExecutor;
import com.drehverschluss.renegades.magic.AbilityPresentation;
import com.drehverschluss.renegades.magic.AbilitySpec;
import com.drehverschluss.renegades.magic.Delivery;
import com.drehverschluss.renegades.magic.ImpactContext;
import com.drehverschluss.renegades.magic.MagicSchool;
import com.drehverschluss.renegades.registry.ModEntityTypes;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A lingering spell area: applies its spell to everyone inside every few ticks for a while.
 */
public class MagicCloud extends Entity {
    private static final EntityDataAccessor<Byte> SCHOOL = SynchedEntityData.defineId(MagicCloud.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(MagicCloud.class, EntityDataSerializers.FLOAT);

    @Nullable
    private AbilitySpec ability;
    @Nullable
    private Delivery.CloudSpec spec;
    @Nullable
    private ImpactContext context;
    private float yaw;

    public MagicCloud(EntityType<? extends MagicCloud> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static MagicCloud spawn(ServerLevel level, LivingEntity caster, AbilitySpec ability, Delivery.CloudSpec spec, ImpactContext context, Vec3 at,
                                   float yaw) {
        MagicCloud cloud = new MagicCloud(ModEntityTypes.MAGIC_CLOUD.get(), level);
        cloud.ability = ability;
        cloud.spec = spec;
        cloud.context = context;
        cloud.yaw = yaw;
        cloud.getEntityData().set(SCHOOL, (byte) ability.lookSchool().ordinal());
        cloud.getEntityData().set(RADIUS, (float) spec.shape().extent());
        cloud.setPos(at);
        level.addFreshEntity(cloud);
        return cloud;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SCHOOL, (byte) 0);
        builder.define(RADIUS, 1.0F);
    }

    public MagicSchool school() {
        MagicSchool[] schools = MagicSchool.values();
        return schools[Math.floorMod(this.getEntityData().get(SCHOOL), schools.length)];
    }

    public float radius() {
        return this.getEntityData().get(RADIUS);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            if (this.tickCount % 2 == 0) {
                float radius = this.radius();
                double angle = this.random.nextDouble() * Math.PI * 2.0;
                double distance = Math.sqrt(this.random.nextDouble()) * radius;
                this.level().addParticle(this.school().dust(1.5F), this.getX() + Math.cos(angle) * distance, this.getY() + 0.2 + this.random.nextDouble(),
                        this.getZ() + Math.sin(angle) * distance, 0.0, 0.02, 0.0);
            }
            return;
        }
        if (this.ability == null || this.spec == null || this.context == null || this.tickCount > this.spec.durationTicks()) {
            this.discard();
            return;
        }
        if ((this.tickCount - 1) % Math.max(1, this.spec.intervalTicks()) == 0) {
            Delivery.Area area = new Delivery.Area(this.spec.shape(), Delivery.Anchor.TARGET, this.spec.height());
            AbilityExecutor.applyArea(level, this.ability, this.context.withDirect(this), area, this.position(), this.yaw, null);
            AbilityPresentation.cloudPulse(level, this.ability, this.position(), (float) this.spec.shape().extent());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
