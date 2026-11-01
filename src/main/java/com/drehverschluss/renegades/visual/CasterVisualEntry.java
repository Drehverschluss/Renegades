package com.drehverschluss.renegades.visual;

import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * The look of one ability from data/[namespace]/renegades/caster_visuals: a spell for all variants and/or one per
 * class variant (e.g. the wizard's fire/frost/arcane). Unset part flags fall back to the ability's, then to true.
 */
public record CasterVisualEntry(@Nullable Choice base, Map<String, Choice> variants) {
    public record Choice(@Nullable ResourceLocation spell, @Nullable Boolean model, @Nullable Boolean particles, @Nullable Boolean sounds) {
    }

    /**
     * The look for a variant (null = the caster has no variants), or null if there is no spell for it.
     */
    @Nullable
    public VisualRef forVariant(@Nullable String variant) {
        Choice choice = variant == null ? null : this.variants.get(variant);
        ResourceLocation spell = choice != null && choice.spell() != null ? choice.spell() : this.base == null ? null : this.base.spell();
        if (spell == null) {
            return null;
        }
        return new VisualRef(spell, flag(choice, this.base, Choice::model), flag(choice, this.base, Choice::particles), flag(choice, this.base, Choice::sounds));
    }

    private static boolean flag(@Nullable Choice primary, @Nullable Choice fallback, Function<Choice, Boolean> getter) {
        Boolean value = primary == null ? null : getter.apply(primary);
        if (value == null && fallback != null) {
            value = getter.apply(fallback);
        }
        return value == null || value;
    }
}
