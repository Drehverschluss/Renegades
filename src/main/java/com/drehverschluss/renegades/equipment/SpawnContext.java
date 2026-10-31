package com.drehverschluss.renegades.equipment;

import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/**
 * Everything the pool conditions are evaluated against.
 *
 * @param distance horizontal distance in blocks between the mob and the world spawn (raw coordinates, also in other dimensions)
 * @param level    mob level from the active level source, empty if there is none
 */
public record SpawnContext(ResourceLocation dimension, BlockPos pos, double distance, OptionalInt level) {
    public static SpawnContext of(Mob mob, ServerLevel level, OptionalInt mobLevel) {
        return at(level, mob.blockPosition(), mobLevel);
    }

    public static SpawnContext at(ServerLevel level, BlockPos pos, OptionalInt mobLevel) {
        BlockPos worldSpawn = level.getServer().overworld().getSharedSpawnPos();
        double dx = pos.getX() - worldSpawn.getX();
        double dz = pos.getZ() - worldSpawn.getZ();
        return new SpawnContext(level.dimension().location(), pos, Math.sqrt(dx * dx + dz * dz), mobLevel);
    }

    public String describe() {
        return "dimension=" + this.dimension
                + ", distance=" + Math.round(this.distance)
                + ", y=" + this.pos.getY()
                + ", level=" + (this.level.isPresent() ? String.valueOf(this.level.getAsInt()) : "none");
    }
}
