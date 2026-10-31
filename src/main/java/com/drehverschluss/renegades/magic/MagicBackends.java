// Based on MagicBackends from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.magic;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.compat.spellpower.SpellPowerCompat;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;

/**
 * Picks the backend once: Spell Power when the mod is installed, otherwise vanilla attributes.
 */
public final class MagicBackends {
    @Nullable
    private static MagicBackend active;

    private MagicBackends() {
    }

    public static MagicBackend active() {
        if (active == null) {
            active = SpellPowerCompat.isLoaded() ? SpellPowerCompat.backend() : VanillaMagicBackend.INSTANCE;
            Renegades.LOGGER.info("Magic backend: {}", active.name());
        }
        return active;
    }

    public static double basePower(LivingEntity caster, MagicSchool school) {
        return active().basePower(caster, school);
    }
}
