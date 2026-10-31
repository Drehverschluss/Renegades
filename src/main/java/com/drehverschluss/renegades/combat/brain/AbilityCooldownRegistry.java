// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.brain;

import java.util.HashMap;
import java.util.Map;

public final class AbilityCooldownRegistry {
    private final Map<String, Integer> cooldownsInTicks = new HashMap<>();

    public boolean isReady(String id) {
        return this.cooldownsInTicks.getOrDefault(id, 0) <= 0;
    }

    public void trigger(String id, int durationTicks) {
        this.cooldownsInTicks.put(id, Math.max(1, durationTicks));
    }

    public void tick() {
        this.cooldownsInTicks.replaceAll((id, ticks) -> Math.max(0, ticks - 1));
    }

    public int remaining(String id) {
        return this.cooldownsInTicks.getOrDefault(id, 0);
    }

    public void clear(String id) {
        this.cooldownsInTicks.put(id, 0);
    }
}
