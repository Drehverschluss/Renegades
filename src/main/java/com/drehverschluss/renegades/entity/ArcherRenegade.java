package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.combat.brain.ArcherBrain;
import com.drehverschluss.renegades.combat.brain.MobBrain;
import com.drehverschluss.renegades.config.RenegadesConfig;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Ranged renegade with a bow or a crossbow (whatever its equipment pool gives it). Shots work like those of vanilla
 * skeletons and pillagers: the arrow comes from the held weapon, so its enchantments (Power, Flame, Punch, Multishot,
 * Piercing) apply, it cannot be picked up, and the archer is its owner (projectile bonuses of mob levels apply).
 */
public class ArcherRenegade extends RenegadeEntity implements RangedAttackMob, CrossbowAttackMob {
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.mob.walk_templar");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mob.idle");
    private static final RawAnimation STANCE = RawAnimation.begin().thenLoop("animation.v2.merc.stance");
    private static final RawAnimation ADVANCE = RawAnimation.begin().thenLoop("animation.v2.merc.advance");
    private static final RawAnimation LATERAL_LEFT = RawAnimation.begin().thenLoop("animation.v2.merc.lateral_left");
    private static final RawAnimation LATERAL_RIGHT = RawAnimation.begin().thenLoop("animation.v2.merc.lateral_right");
    private static final RawAnimation BACKSTEP = RawAnimation.begin().thenLoop("animation.v2.merc.backstep");
    // Older ANARCHY bow poses (body turned sideways, bow arm stretched towards the target), used with a bow.
    private static final RawAnimation BOW_STANCE = RawAnimation.begin().thenLoop("animation.mob.idleshoot");
    private static final RawAnimation BOW_WALK = RawAnimation.begin().thenLoop("animation.mob.shootwalk");
    private static final RawAnimation BOW_BACKSTEP = RawAnimation.begin().thenLoop("animation.unknown.walk_backwards_shoot");
    // Same as skeletons and pillagers: arrow speed 1.6, spread 14 - 4 x difficulty.
    private static final float SHOT_VELOCITY = 1.6F;
    // Full draw of a bow (BowItem#getPowerForTime at 20 ticks); vanilla base arrow damage is 2 x this.
    private static final float BOW_POWER = 1.0F;
    private static final int DEFAULT_CROSSBOW_CHARGE = 25;

    public ArcherRenegade(EntityType<? extends ArcherRenegade> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 0.0D);
    }

    @Nullable
    @Override
    protected MobBrain createBrain() {
        return new ArcherBrain(this);
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return weapon instanceof BowItem || weapon instanceof CrossbowItem;
    }

    public boolean hasRangedWeapon() {
        return this.getMainHandItem().getItem() instanceof BowItem || this.getMainHandItem().getItem() instanceof CrossbowItem;
    }

    public boolean hasCrossbow() {
        return this.getMainHandItem().getItem() instanceof CrossbowItem;
    }

    /**
     * Charging time of the held crossbow relative to an unenchanted one (Quick Charge lowers it); 1 for bows.
     */
    public double reloadFactor() {
        ItemStack weapon = this.getMainHandItem();
        if (weapon.getItem() instanceof CrossbowItem) {
            return Math.max(0.2D, CrossbowItem.getChargeDuration(weapon, this) / (double) DEFAULT_CROSSBOW_CHARGE);
        }
        return 1.0D;
    }

    /**
     * Raises the weapon: a bow is drawn and a crossbow loaded, both visible through the vanilla item models.
     */
    public void startAiming() {
        if (!this.hasRangedWeapon()) {
            return;
        }
        ItemStack weapon = this.getMainHandItem();
        if (weapon.getItem() instanceof CrossbowItem && !CrossbowItem.isCharged(weapon)) {
            CrossbowLoader.load(this, weapon);
            this.playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 0.8F, 1.0F);
        }
        if (!this.isUsingItem()) {
            this.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    public void stopAiming() {
        if (this.isUsingItem()) {
            this.stopUsingItem();
        }
    }

    /**
     * Shoots the held weapon at the target. The caller checks line of sight and the line of fire.
     */
    public void shootAt(LivingEntity target) {
        if (this.level().isClientSide() || !this.hasRangedWeapon()) {
            return;
        }
        this.lookAt(target, 30.0F, 30.0F);
        if (this.hasCrossbow()) {
            ItemStack weapon = this.getMainHandItem();
            // Vanilla wears the crossbow down with every shot; renegades keep their gear intact (see dropUndamaged).
            int damage = weapon.getDamageValue();
            if (!CrossbowItem.isCharged(weapon)) {
                CrossbowLoader.load(this, weapon);
            }
            this.performCrossbowAttack(this, SHOT_VELOCITY);
            if (weapon.isDamageableItem()) {
                weapon.setDamageValue(damage);
            }
            // Reloaded at once, so the crossbow is shown loaded until the next shot.
            CrossbowLoader.load(this, weapon);
        } else {
            this.performRangedAttack(target, BOW_POWER);
        }
    }

    /**
     * Bow shot like AbstractSkeleton#performRangedAttack.
     */
    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(this, item -> item instanceof BowItem);
        ItemStack weapon = this.getItemInHand(hand);
        ItemStack ammo = this.getProjectile(weapon);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, power, weapon);
        if (weapon.getItem() instanceof ProjectileWeaponItem weaponItem) {
            arrow = weaponItem.customArrow(arrow, ammo, weapon);
        }
        arrow.setBaseDamage(arrow.getBaseDamage() * RenegadesConfig.DAMAGE_MULTIPLIER.get());
        double dx = target.getX() - this.getX();
        double dy = target.getY(1.0D / 3.0D) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2D, dz, SHOT_VELOCITY, this.inaccuracy());
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(arrow);
    }

    private float inaccuracy() {
        return (float) (14 - this.level().getDifficulty().getId() * 4);
    }

    @Override
    public void setChargingCrossbow(boolean charging) {
    }

    @Override
    public void onCrossbowAttackPerformed() {
        // Crossbow bolts get the same strength multiplier as bow arrows.
        double multiplier = RenegadesConfig.DAMAGE_MULTIPLIER.get();
        if (multiplier != 1.0D) {
            for (AbstractArrow bolt : this.level().getEntitiesOfClass(AbstractArrow.class, this.getBoundingBox().inflate(3.0D),
                    bolt -> bolt.getOwner() == this && bolt.tickCount == 0)) {
                bolt.setBaseDamage(bolt.getBaseDamage() * multiplier);
            }
        }
    }

    /**
     * Whether an arrow from the eyes to the target would pass through someone the archer must not hit (allies,
     * other renegades, bystanders).
     */
    public boolean isLineOfFireBlocked(LivingEntity target) {
        Vec3 from = this.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        AABB sweep = new AABB(from, to).inflate(1.0D);
        return !this.level().getEntitiesOfClass(LivingEntity.class, sweep, other -> other != this && other != target && other.isAlive()
                && !other.isSpectator() && !this.canHarm(other)
                && other.getBoundingBox().inflate(0.3D).clip(from, to).isPresent()).isEmpty();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (this.isAggressive()) {
                boolean bow = this.getMainHandItem().getItem() instanceof BowItem;
                return state.setAndContinue(switch (this.getCombatMoveState()) {
                    case STANCE -> bow ? BOW_STANCE : STANCE;
                    case ADVANCE -> bow ? BOW_WALK : ADVANCE;
                    case LATERAL_LEFT -> bow ? BOW_WALK : LATERAL_LEFT;
                    case LATERAL_RIGHT -> bow ? BOW_WALK : LATERAL_RIGHT;
                    case BACKSTEP -> bow ? BOW_BACKSTEP : BACKSTEP;
                });
            }
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }).setAnimationSpeedHandler(archer -> archer.isAggressive() && archer.getCombatMoveState() != CombatMoveState.STANCE
                ? (double) archer.getCombatMoveSpeed() : 1.0D));

        AnimationController<ArcherRenegade> actions = new AnimationController<>(this, ACTION_CONTROLLER, 0, state -> PlayState.STOP);
        for (String animation : ArcherBrain.ACTION_ANIMATIONS) {
            actions.triggerableAnim(animation, RawAnimation.begin().thenPlay(animation));
        }
        controllers.add(actions);
    }
}
