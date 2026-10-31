package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.magic.MagicSchool;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;

/**
 * School of a wizard (like the wizard classes of the RPG Series): decides the spell school, the extra effect of its
 * spells and the colour of a dyeable robe.
 */
public enum WizardVariant {
    // Like the original fire mage: hits set the target on fire.
    FIRE(MagicSchool.FIRE, 0xB02E26, 1.0),
    // Hits slow the target.
    FROST(MagicSchool.FROST, 0x3AB3DA, 1.0),
    // No extra effect, but faster projectiles.
    ARCANE(MagicSchool.ARCANE, 0x8932B8, 1.25);

    private final MagicSchool school;
    private final int robeColor;
    private final double projectileSpeedFactor;

    WizardVariant(MagicSchool school, int robeColor, double projectileSpeedFactor) {
        this.school = school;
        this.robeColor = robeColor;
        this.projectileSpeedFactor = projectileSpeedFactor;
    }

    public MagicSchool school() {
        return this.school;
    }

    public int robeColor() {
        return this.robeColor;
    }

    public double projectileSpeedFactor() {
        return this.projectileSpeedFactor;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static WizardVariant byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (WizardVariant variant : values()) {
            if (variant.id().equals(id.toLowerCase(Locale.ROOT))) {
                return variant;
            }
        }
        return null;
    }

    public static WizardVariant random(RandomSource random) {
        return values()[random.nextInt(values().length)];
    }
}
