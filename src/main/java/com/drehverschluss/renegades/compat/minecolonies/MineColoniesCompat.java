package com.drehverschluss.renegades.compat.minecolonies;

import com.drehverschluss.renegades.config.RenegadesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Entry point for the optional MineColonies integration (noSpawnInColonies). Must not reference any MineColonies class,
 * so the mod runs without it; the API is only touched in ColonyLookup, which is loaded when MineColonies is present.
 */
public final class MineColoniesCompat {
    public static final String MOD_ID = "minecolonies";

    private MineColoniesCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Whether natural renegade spawns are forbidden at this position: noSpawnInColonies is on, MineColonies is loaded
     * and the position lies in a colony.
     */
    public static boolean blocksNaturalSpawn(Level level, BlockPos pos) {
        return RenegadesConfig.NO_SPAWN_IN_COLONIES.get() && isLoaded() && ColonyLookup.isInColony(level, pos);
    }
}
