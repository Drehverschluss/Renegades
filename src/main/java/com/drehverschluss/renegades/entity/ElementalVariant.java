package com.drehverschluss.renegades.entity;

import com.drehverschluss.renegades.magic.MagicSchool;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;

/**
 * Element of an elemental wizard (like the classes of Elemental Wizards RPG): decides its spells, their school and the
 * colour of a dyeable robe.
 */
public enum ElementalVariant {
    AQUA(MagicSchool.WATER, 0x2F6FB8),
    TERRA(MagicSchool.EARTH, 0x7A5A32),
    WIND(MagicSchool.AIR, 0xB9E3DA);

    private final MagicSchool school;
    private final int robeColor;

    ElementalVariant(MagicSchool school, int robeColor) {
        this.school = school;
        this.robeColor = robeColor;
    }

    public MagicSchool school() {
        return this.school;
    }

    public int robeColor() {
        return this.robeColor;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static ElementalVariant byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (ElementalVariant variant : values()) {
            if (variant.id().equals(id.toLowerCase(Locale.ROOT))) {
                return variant;
            }
        }
        return null;
    }

    public static ElementalVariant random(RandomSource random) {
        return values()[random.nextInt(values().length)];
    }
}
