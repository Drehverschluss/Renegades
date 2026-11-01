package com.drehverschluss.renegades.visual;

import net.minecraft.resources.ResourceLocation;

/**
 * The look a caster's ability gets from a spell of another mod: the spell id and which parts of its look are used.
 * Parts that are off keep the built-in look.
 */
public record VisualRef(ResourceLocation spell, boolean model, boolean particles, boolean sounds) {
}
