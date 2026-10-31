// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.brain;

import java.util.List;

public record PhaseTransition(
    float healthThreshold, BrainState forcedStance, List<BrainState> lockedOutStates, List<BrainState> unlockedStates, Runnable onEnter
) {
    public boolean isTriggered(float currentHealthPct) {
        return currentHealthPct <= this.healthThreshold;
    }
}
