package com.drehverschluss.renegades.compat.dynamicdifficulty;

import net.neoforged.fml.ModList;

/**
 * Entry point for the optional Dynamic Difficulty integration. Must not reference any Dynamic Difficulty class.
 */
public final class DynamicDifficultyCompat {
    public static final String MOD_ID = "dynamic_difficulty";

    private DynamicDifficultyCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
