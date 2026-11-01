package com.drehverschluss.renegades.magic;

import com.drehverschluss.renegades.Renegades;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

/**
 * Status effects of other mods, looked up by registry id at runtime (no compile dependency on the mod).
 */
public final class ExternalEffects {
    private ExternalEffects() {
    }

    /**
     * The effect with this id, or the vanilla fallback if it is not registered (its mod is missing).
     */
    public static Holder<MobEffect> orElse(String id, Holder<MobEffect> fallback) {
        ResourceLocation location = ResourceLocation.parse(id);
        Holder<MobEffect> found = BuiltInRegistries.MOB_EFFECT.getHolder(location).<Holder<MobEffect>>map(holder -> holder).orElse(null);
        if (found == null) {
            Renegades.LOGGER.debug("Status effect {} is not registered, using {} instead", id, fallback.getRegisteredName());
            return fallback;
        }
        return found;
    }
}
