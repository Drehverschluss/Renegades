package com.drehverschluss.renegades.level;

import java.util.OptionalInt;
import net.minecraft.world.entity.Mob;

/**
 * Source of mob levels for the min_level/max_level pool conditions.
 */
public interface RenegadeLevelProvider {
    /**
     * Called from finalizeSpawn, before the mob is added to the world. Must return the level the mob will actually
     * have; implementations may assign it right away for that. Empty if the mob gets no level.
     */
    OptionalInt levelForSpawn(Mob mob);

    /**
     * Current level of a mob that is already in the world, empty if it has none.
     */
    OptionalInt currentLevel(Mob mob);
}
