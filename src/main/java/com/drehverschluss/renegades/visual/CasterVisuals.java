package com.drehverschluss.renegades.visual;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.compat.spellengine.SpellEngineCompat;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.RenegadeClasses;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Which look a caster's ability gets (server side): decided from the casterVisuals config, the caster_visuals data and
 * whether the assigned spell exists. Generic for every class: an ability with a visual key and a class with variants
 * (RenegadeEntity#visualVariant) need no special code.
 */
public final class CasterVisuals {
    private static volatile Map<EntityType<?>, Map<String, CasterVisualEntry>> entries = Map.of();
    private static final Set<String> warned = ConcurrentHashMap.newKeySet();

    private CasterVisuals() {
    }

    static void set(Map<EntityType<?>, Map<String, CasterVisualEntry>> loaded) {
        Map<EntityType<?>, Map<String, CasterVisualEntry>> copy = new LinkedHashMap<>();
        loaded.forEach((type, abilities) -> copy.put(type, Map.copyOf(abilities)));
        entries = Map.copyOf(copy);
        warned.clear();
    }

    public static Map<EntityType<?>, Map<String, CasterVisualEntry>> all() {
        return entries;
    }

    public static boolean useSpellLook() {
        return "auto".equals(RenegadesConfig.CASTER_VISUALS.get()) && SpellEngineCompat.isLoaded();
    }

    /**
     * Whether this caster's spells take the look from caster_visuals: with casterVisuals = "auto", and always for
     * classes of mods that need Spell Engine (they have no built-in look of their own, see RenegadeClasses).
     */
    public static boolean useSpellLook(LivingEntity caster) {
        return SpellEngineCompat.isLoaded()
                && ("auto".equals(RenegadesConfig.CASTER_VISUALS.get()) || RenegadeClasses.alwaysSpellEngineLook(caster.getType()));
    }

    /**
     * The look of the caster's ability with this visual key, or null for the built-in look (config "builtin" for the
     * original classes, no Spell Engine, no entry, or the spell does not exist; the last case is logged once per spell).
     */
    @Nullable
    public static VisualRef resolve(LivingEntity caster, @Nullable String visualKey) {
        if (visualKey == null || !useSpellLook(caster)) {
            return null;
        }
        CasterVisualEntry entry = entries.getOrDefault(caster.getType(), Map.of()).get(visualKey);
        if (entry == null) {
            return null;
        }
        String variant = caster instanceof RenegadeEntity renegade ? renegade.visualVariant() : null;
        VisualRef ref = entry.forVariant(variant);
        if (ref == null || !spellExists(caster.level(), ref)) {
            return null;
        }
        return ref;
    }

    private static boolean spellExists(Level level, VisualRef ref) {
        if (SpellEngineCompat.spellExists(level, ref.spell())) {
            return true;
        }
        if (warned.add(ref.spell().toString())) {
            Renegades.LOGGER.warn("Caster visuals: spell {} does not exist, the built-in look is used instead", ref.spell());
        }
        return false;
    }
}
