package com.drehverschluss.renegades.magic;

import net.minecraft.world.entity.LivingEntity;

/**
 * A caster that wants to know how much its healing spells actually healed (debug statistics).
 */
public interface HealingCaster {
    void onHealed(LivingEntity target, float amount);
}
