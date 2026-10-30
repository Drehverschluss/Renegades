package com.drehverschluss.renegades.compat.dynamicdifficulty;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.level.RenegadeLevelProvider;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import java.util.OptionalInt;
import net.minecraft.world.entity.Mob;

/**
 * Level source backed by Dynamic Difficulty. Only loaded when Dynamic Difficulty is installed.
 * <p>
 * Dynamic Difficulty assigns levels on EntityJoinLevelEvent, i.e. after finalizeSpawn, and its calculation includes a
 * random bonus. So the level is calculated here with the same API call Dynamic Difficulty uses on join and committed
 * right away; on join, Dynamic Difficulty then sees an existing level and only applies its attribute bonuses.
 */
public final class DynamicDifficultyLevelProvider implements RenegadeLevelProvider {
    private boolean failureLogged;

    @Override
    public OptionalInt levelForSpawn(Mob mob) {
        try {
            if (!LevelingAPI.canHaveLevel(mob)) {
                return OptionalInt.empty();
            }
            if (!LevelingAPI.hasLevel(mob)) {
                LevelingAPI.setAndUpdateLevel(mob, LevelingAPI.calculateLevelForEntity(mob));
            }
            return OptionalInt.of(LevelingAPI.getLevel(mob));
        } catch (RuntimeException e) {
            logFailure(e);
            return OptionalInt.empty();
        }
    }

    @Override
    public OptionalInt currentLevel(Mob mob) {
        try {
            return LevelingAPI.hasLevel(mob) ? OptionalInt.of(LevelingAPI.getLevel(mob)) : OptionalInt.empty();
        } catch (RuntimeException e) {
            logFailure(e);
            return OptionalInt.empty();
        }
    }

    private void logFailure(RuntimeException e) {
        if (!this.failureLogged) {
            this.failureLogged = true;
            Renegades.LOGGER.warn("Could not get a mob level from Dynamic Difficulty; level conditions are ignored for affected mobs", e);
        }
    }
}
