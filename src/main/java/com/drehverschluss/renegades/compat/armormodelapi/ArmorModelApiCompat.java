package com.drehverschluss.renegades.compat.armormodelapi;

import net.neoforged.fml.ModList;

/**
 * Entry point for the optional Armor Model API integration. Must not reference any Armor Model API class.
 */
public final class ArmorModelApiCompat {
    public static final String MOD_ID = "armor_model_api";

    private ArmorModelApiCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}