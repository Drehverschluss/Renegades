package com.drehverschluss.renegades.compat.spellpower;

import com.drehverschluss.renegades.magic.MagicBackend;
import net.neoforged.fml.ModList;

/**
 * Entry point for the optional Spell Power API integration. Must not reference any Spell Power class, so the mod
 * runs without it.
 */
public final class SpellPowerCompat {
    public static final String MOD_ID = "spell_power";

    private SpellPowerCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Only call when isLoaded() is true.
     */
    public static MagicBackend backend() {
        return SpellPowerMagicBackend.INSTANCE;
    }
}
