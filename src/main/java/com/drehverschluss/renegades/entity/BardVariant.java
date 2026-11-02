package com.drehverschluss.renegades.entity;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;

/**
 * Kind of a bard (like the instruments of Bards RPG): the battle bard plays the lute and makes its allies hit harder, the
 * guardian bard plays the lyre and protects and heals them. Also the colour of a dyeable robe.
 */
public enum BardVariant {
    BATTLE(0xA8322D),
    GUARDIAN(0x3A7BC8);

    private final int robeColor;

    BardVariant(int robeColor) {
        this.robeColor = robeColor;
    }

    public int robeColor() {
        return this.robeColor;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static BardVariant byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (BardVariant variant : values()) {
            if (variant.id().equals(id.toLowerCase(Locale.ROOT))) {
                return variant;
            }
        }
        return null;
    }

    public static BardVariant random(RandomSource random) {
        return values()[random.nextInt(values().length)];
    }
}
