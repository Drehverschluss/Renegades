// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: no spell casts (AbilitySpec), no slash ribbons and no client attack registry; default animation controller
// is RenegadeEntity.ACTION_CONTROLLER.
package com.drehverschluss.renegades.combat;

import com.drehverschluss.renegades.combat.geometry.AttackVolume;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public final class CombatAction {
    private final String id;
    private final String animationController;
    @Nullable
    private final String animation;
    private final int windupTicks;
    private final int activeTicks;
    private final int recoveryTicks;
    private final AttackMotion motion;
    private final double advance;
    private final double windupDrift;
    private final double committedSpeed;
    private final int committedTicks;
    private final double committedLift;
    private final double committedCorrection;
    private final TrackingMode windupTracking;
    private final TrackingMode activeTracking;
    private final TrackingMode recoveryTracking;
    @Nullable
    private final AttackVolume volume;
    private final boolean usesWeaponDamage;
    private final float damageScale;
    private final double knockback;
    private final double knockbackVertical;
    private final boolean multiHit;
    private final int multiHitCooldown;
    @Nullable
    private final SlashVisual slash;
    private final int cooldownTicks;
    private final int urgentRecoveryTrim;
    @Nullable
    private final Consumer<ActionContext> onStart;
    @Nullable
    private final Consumer<ActionContext> onActiveStart;
    @Nullable
    private final Consumer<ActionContext> onActiveTick;
    @Nullable
    private final Consumer<ActionContext> onRecoveryStart;
    @Nullable
    private final Consumer<ActionContext> onFinish;

    private CombatAction(CombatAction.Builder builder) {
        this.id = builder.id;
        this.animationController = builder.animationController;
        this.animation = builder.animation;
        this.windupTicks = builder.windupTicks;
        this.activeTicks = builder.activeTicks;
        this.recoveryTicks = builder.recoveryTicks;
        this.motion = builder.motion;
        this.advance = builder.advance;
        this.windupDrift = builder.windupDrift;
        this.committedSpeed = builder.committedSpeed;
        this.committedTicks = builder.committedTicks > 0 ? builder.committedTicks : builder.activeTicks;
        this.committedLift = builder.committedLift;
        this.committedCorrection = builder.committedCorrection;
        this.windupTracking = builder.windupTracking;
        this.activeTracking = builder.activeTracking;
        this.recoveryTracking = builder.recoveryTracking;
        this.volume = builder.volume;
        this.usesWeaponDamage = builder.usesWeaponDamage;
        this.damageScale = builder.damageScale;
        this.knockback = builder.knockback;
        this.knockbackVertical = builder.knockbackVertical;
        this.multiHit = builder.multiHit;
        this.multiHitCooldown = builder.multiHitCooldown;
        this.slash = builder.slash;
        this.cooldownTicks = builder.cooldownTicks;
        this.urgentRecoveryTrim = builder.urgentRecoveryTrim;
        this.onStart = builder.onStart;
        this.onActiveStart = builder.onActiveStart;
        this.onActiveTick = builder.onActiveTick;
        this.onRecoveryStart = builder.onRecoveryStart;
        this.onFinish = builder.onFinish;
    }

    public static CombatAction.Builder builder(String id) {
        return new CombatAction.Builder(id);
    }

    public String id() {
        return this.id;
    }

    public String animationController() {
        return this.animationController;
    }

    @Nullable
    public String animation() {
        return this.animation;
    }

    public int windupTicks() {
        return this.windupTicks;
    }

    public int activeTicks() {
        return this.activeTicks;
    }

    public int recoveryTicks() {
        return this.recoveryTicks;
    }

    public int totalTicks() {
        return this.windupTicks + this.activeTicks + this.recoveryTicks;
    }

    public AttackMotion motion() {
        return this.motion;
    }

    public double advance() {
        return this.advance;
    }

    public double windupDrift() {
        return this.windupDrift;
    }

    public double committedSpeed() {
        return this.committedSpeed;
    }

    public int committedTicks() {
        return this.committedTicks;
    }

    public double committedLift() {
        return this.committedLift;
    }

    public double committedCorrection() {
        return this.committedCorrection;
    }

    @Nullable
    public AttackVolume volume() {
        return this.volume;
    }

    public boolean usesWeaponDamage() {
        return this.usesWeaponDamage;
    }

    public float damageScale() {
        return this.damageScale;
    }

    public double knockback() {
        return this.knockback;
    }

    public double knockbackVertical() {
        return this.knockbackVertical;
    }

    public boolean multiHit() {
        return this.multiHit;
    }

    public int multiHitCooldown() {
        return this.multiHitCooldown;
    }

    @Nullable
    public SlashVisual slash() {
        return this.slash;
    }

    public int cooldownTicks() {
        return this.cooldownTicks;
    }

    public int urgentRecoveryTrim() {
        return this.urgentRecoveryTrim;
    }

    public TrackingMode tracking(AttackPhase phase) {
        return switch (phase) {
            case WINDUP -> this.windupTracking;
            case ACTIVE -> this.activeTracking;
            case RECOVERY, FINISHED -> this.recoveryTracking;
        };
    }

    void fireStart(ActionContext context) {
        if (this.onStart != null) {
            this.onStart.accept(context);
        }
    }

    void fireActiveStart(ActionContext context) {
        if (this.onActiveStart != null) {
            this.onActiveStart.accept(context);
        }
    }

    void fireActiveTick(ActionContext context) {
        if (this.onActiveTick != null) {
            this.onActiveTick.accept(context);
        }
    }

    void fireRecoveryStart(ActionContext context) {
        if (this.onRecoveryStart != null) {
            this.onRecoveryStart.accept(context);
        }
    }

    void fireFinish(ActionContext context) {
        if (this.onFinish != null) {
            this.onFinish.accept(context);
        }
    }

    @Override
    public String toString() {
        return "CombatAction[" + this.id + " " + this.motion + " " + this.windupTicks + "/" + this.activeTicks + "/" + this.recoveryTicks + "]";
    }

    public static final class Builder {
        private final String id;
        private String animationController = RenegadeEntity.ACTION_CONTROLLER;
        @Nullable
        private String animation;
        private int windupTicks = 8;
        private int activeTicks = 4;
        private int recoveryTicks = 8;
        private AttackMotion motion = AttackMotion.PLANTED;
        private double advance;
        private double windupDrift;
        private double committedSpeed = 0.6;
        private int committedTicks;
        private double committedLift;
        private double committedCorrection;
        private TrackingMode windupTracking = TrackingMode.REDUCED;
        private TrackingMode activeTracking = TrackingMode.LOCKED;
        private TrackingMode recoveryTracking = TrackingMode.MINIMAL;
        @Nullable
        private AttackVolume volume;
        private boolean usesWeaponDamage = true;
        private float damageScale = 1.0F;
        private double knockback = 0.4;
        private double knockbackVertical;
        private boolean multiHit;
        private int multiHitCooldown = 10;
        @Nullable
        private SlashVisual slash;
        private int cooldownTicks = 40;
        private int urgentRecoveryTrim;
        @Nullable
        private Consumer<ActionContext> onStart;
        @Nullable
        private Consumer<ActionContext> onActiveStart;
        @Nullable
        private Consumer<ActionContext> onActiveTick;
        @Nullable
        private Consumer<ActionContext> onRecoveryStart;
        @Nullable
        private Consumer<ActionContext> onFinish;

        private Builder(String id) {
            this.id = id;
        }

        public CombatAction.Builder animation(String controller, String animation) {
            this.animationController = controller;
            this.animation = animation;
            return this;
        }

        public CombatAction.Builder animation(String animation) {
            this.animation = animation;
            return this;
        }

        public CombatAction.Builder timing(int windup, int active, int recovery) {
            this.windupTicks = windup;
            this.activeTicks = active;
            this.recoveryTicks = recovery;
            return this;
        }

        public CombatAction.Builder motion(AttackMotion motion) {
            this.motion = motion;
            return this;
        }

        public CombatAction.Builder advance(double blocks) {
            this.advance = blocks;
            return this;
        }

        public CombatAction.Builder windupDrift(double blocks) {
            this.windupDrift = blocks;
            return this;
        }

        public CombatAction.Builder committed(double speed, int ticks, double lift, double correction) {
            this.committedSpeed = speed;
            this.committedTicks = ticks;
            this.committedLift = lift;
            this.committedCorrection = correction;
            return this;
        }

        public CombatAction.Builder tracking(TrackingMode windup, TrackingMode active) {
            this.windupTracking = windup;
            this.activeTracking = active;
            return this;
        }

        public CombatAction.Builder recoveryTracking(TrackingMode tracking) {
            this.recoveryTracking = tracking;
            return this;
        }

        public CombatAction.Builder volume(AttackVolume volume) {
            this.volume = volume;
            return this;
        }

        /**
         * The first victim takes a full weapon hit (doHurtTarget); further victims of the same swing take the weapon
         * hit at damage scale 1.
         */
        public CombatAction.Builder weaponDamage() {
            this.usesWeaponDamage = true;
            this.damageScale = 1.0F;
            return this;
        }

        /**
         * Every victim takes a melee hit with attack damage scaled by this factor.
         */
        public CombatAction.Builder scaledDamage(float scale) {
            this.usesWeaponDamage = false;
            this.damageScale = scale;
            return this;
        }

        public CombatAction.Builder knockback(double horizontal, double vertical) {
            this.knockback = horizontal;
            this.knockbackVertical = vertical;
            return this;
        }

        public CombatAction.Builder multiHit(int cooldown) {
            this.multiHit = true;
            this.multiHitCooldown = cooldown;
            return this;
        }

        public CombatAction.Builder slash(SlashVisual slash) {
            this.slash = slash;
            return this;
        }

        public CombatAction.Builder cooldown(int ticks) {
            this.cooldownTicks = ticks;
            return this;
        }

        public CombatAction.Builder urgentRecoveryTrim(int ticks) {
            this.urgentRecoveryTrim = ticks;
            return this;
        }

        public CombatAction.Builder onStart(Consumer<ActionContext> hook) {
            this.onStart = hook;
            return this;
        }

        public CombatAction.Builder onActiveStart(Consumer<ActionContext> hook) {
            this.onActiveStart = hook;
            return this;
        }

        public CombatAction.Builder onActiveTick(Consumer<ActionContext> hook) {
            this.onActiveTick = hook;
            return this;
        }

        public CombatAction.Builder onRecoveryStart(Consumer<ActionContext> hook) {
            this.onRecoveryStart = hook;
            return this;
        }

        public CombatAction.Builder onFinish(Consumer<ActionContext> hook) {
            this.onFinish = hook;
            return this;
        }

        public CombatAction build() {
            return new CombatAction(this);
        }
    }
}
