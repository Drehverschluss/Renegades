package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.combat.brain.BerserkerBrain;
import com.drehverschluss.renegades.combat.brain.MobBrain;
import com.drehverschluss.renegades.config.RenegadesConfig;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Berserker of the Berserker RPG class: always presses forward with a two-handed axe and gets more dangerous the more
 * health it has lost (rage). No parry, no block, no dodge, no retreat. Only available with that mod (see
 * RenegadeClasses).
 */
public class BerserkerRenegade extends RenegadeEntity {
    private static final RawAnimation STANCE = RawAnimation.begin().thenLoop("animation.unknown.idle_2h");
    private static final RawAnimation ADVANCE = RawAnimation.begin().thenLoop("animation.templar.heavy_run");
    private static final RawAnimation LATERAL_LEFT = RawAnimation.begin().thenLoop("animation.v2.jugg.lateral_left");
    private static final RawAnimation LATERAL_RIGHT = RawAnimation.begin().thenLoop("animation.v2.jugg.lateral_right");
    private static final RawAnimation BACKSTEP = RawAnimation.begin().thenLoop("animation.unknown.walk_backwards2_2h");
    private static final String TAG_SECOND_WIND = "BerserkerSecondWindUsed";
    // Rage starts below this health fraction and is full at RAGE_FULL.
    private static final float RAGE_START = 0.5F;
    private static final float RAGE_FULL = 0.1F;
    public static final double RAGE_DAMAGE = 0.4;
    public static final double RAGE_SPEED = 0.25;
    private static final ResourceLocation RAGE_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(Renegades.MODID, "berserker_rage_damage");
    private static final ResourceLocation RAGE_SPEED_ID = ResourceLocation.fromNamespaceAndPath(Renegades.MODID, "berserker_rage_speed");

    private boolean secondWindUsed;
    private float appliedRage = -1.0F;

    public BerserkerRenegade(EntityType<? extends BerserkerRenegade> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 65.0D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 0.0D);
    }

    @Nullable
    @Override
    protected MobBrain createBrain() {
        return new BerserkerBrain(this);
    }

    /**
     * Rage from 0 (at 50 % health or more) to 1 (at 10 % or less), linear in between; 0 with enableBerserkerRage = false.
     */
    public float rage() {
        if (!RenegadesConfig.ENABLE_BERSERKER_RAGE.get()) {
            return 0.0F;
        }
        float health = this.getHealth() / Math.max(1.0F, this.getMaxHealth());
        return Mth.clamp((RAGE_START - health) / (RAGE_START - RAGE_FULL), 0.0F, 1.0F);
    }

    public boolean isSecondWindUsed() {
        return this.secondWindUsed;
    }

    public void markSecondWindUsed() {
        this.secondWindUsed = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount % 5 == 0) {
            this.updateRageModifiers();
        }
    }

    /**
     * Rage as attribute modifiers on attack damage and movement speed; removed again when healed above 50 %. Rounded to
     * steps of 5 % so the attributes are not rewritten every few ticks.
     */
    private void updateRageModifiers() {
        float rage = Math.round(this.rage() * 20.0F) / 20.0F;
        if (rage == this.appliedRage) {
            return;
        }
        this.appliedRage = rage;
        setModifier(this.getAttribute(Attributes.ATTACK_DAMAGE), RAGE_DAMAGE_ID, RAGE_DAMAGE * rage);
        setModifier(this.getAttribute(Attributes.MOVEMENT_SPEED), RAGE_SPEED_ID, RAGE_SPEED * rage);
    }

    private static void setModifier(@Nullable AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(id);
        if (amount > 0.0) {
            attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /**
     * The rage modifier currently on this attribute (debug output).
     */
    public double rageModifier(Holder<Attribute> attribute) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance == null) {
            return 0.0;
        }
        AttributeModifier modifier = instance.getModifier(attribute == Attributes.ATTACK_DAMAGE ? RAGE_DAMAGE_ID : RAGE_SPEED_ID);
        return modifier == null ? 0.0 : modifier.amount();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_SECOND_WIND, this.secondWindUsed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.secondWindUsed = tag.getBoolean(TAG_SECOND_WIND);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new MovementAnimationController<>(this, "movement", 5, state -> {
            if (this.isAggressive()) {
                return state.setAndContinue(switch (this.getCombatMoveState()) {
                    case STANCE -> STANCE;
                    case ADVANCE -> ADVANCE;
                    case LATERAL_LEFT -> LATERAL_LEFT;
                    case LATERAL_RIGHT -> LATERAL_RIGHT;
                    case BACKSTEP -> BACKSTEP;
                });
            }
            return state.setAndContinue(this.isWalking() ? WALK : IDLE);
        }).setAnimationSpeedHandler(RenegadeEntity::movementAnimationSpeed));

        AnimationController<BerserkerRenegade> actions = new AnimationController<>(this, ACTION_CONTROLLER, 0, state -> PlayState.STOP);
        for (String animation : BerserkerBrain.ACTION_ANIMATIONS) {
            actions.triggerableAnim(animation, RawAnimation.begin().thenPlay(animation));
        }
        controllers.add(actions);
    }
}
