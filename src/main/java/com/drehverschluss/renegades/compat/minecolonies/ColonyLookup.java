package com.drehverschluss.renegades.compat.minecolonies;

import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The MineColonies API call; only loaded when MineColonies is installed (see MineColoniesCompat).
 */
final class ColonyLookup {
    private ColonyLookup() {
    }

    static boolean isInColony(Level level, BlockPos pos) {
        return IColonyManager.getInstance().isCoordinateInAnyColony(level, pos);
    }
}
