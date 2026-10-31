// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.brain;

import java.util.function.Predicate;

public record Transition(BrainState from, Predicate<AIStimulus> condition, BrainState to, int priority) {
}
