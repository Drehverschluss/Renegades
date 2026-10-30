package com.drehverschluss.renegades.client.model;

import java.util.Set;

/**
 * Bone names of renegade.geo.json that rendering code relies on.
 */
public final class RenegadeBones {
    public static final String ARMOR_HEAD = "armorHead";
    public static final String ARMOR_BODY = "armorBody";
    public static final String ARMOR_BELT = "armorBelt";
    public static final String ARMOR_RIGHT_ARM = "armorRightArm";
    public static final String ARMOR_LEFT_ARM = "armorLeftArm";
    public static final String ARMOR_RIGHT_LEG = "armorRightLeg";
    public static final String ARMOR_LEFT_LEG = "armorLeftLeg";
    public static final String ARMOR_RIGHT_BOOT = "armorRightBoot";
    public static final String ARMOR_LEFT_BOOT = "armorLeftBoot";
    public static final String ITEM = "itemBone";

    /**
     * Bones whose cubes only serve as size reference for armor rendering and must never be drawn.
     */
    public static final Set<String> REFERENCE_ONLY = Set.of(ARMOR_BELT, ARMOR_RIGHT_BOOT, ARMOR_LEFT_BOOT);

    private RenegadeBones() {
    }
}