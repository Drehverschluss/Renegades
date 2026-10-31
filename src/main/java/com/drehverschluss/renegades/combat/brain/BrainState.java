// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.brain;

public interface BrainState {
    String id();

    default boolean isTerminal() {
        return false;
    }
}
