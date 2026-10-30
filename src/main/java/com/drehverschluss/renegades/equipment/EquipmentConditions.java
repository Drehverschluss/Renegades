package com.drehverschluss.renegades.equipment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional spawn conditions of a pool entry; all given conditions must match. Ranges are inclusive.
 * Level bounds only apply when the mob has a level (see RenegadeLevels); otherwise they count as met.
 */
public record EquipmentConditions(Set<ResourceLocation> dimensions, IntBounds distance, IntBounds y, IntBounds level) {
    public static final EquipmentConditions NONE = new EquipmentConditions(Set.of(), IntBounds.ANY, IntBounds.ANY, IntBounds.ANY);

    public boolean test(SpawnContext context) {
        return (this.dimensions.isEmpty() || this.dimensions.contains(context.dimension()))
                && this.distance.test(context.distance())
                && this.y.test(context.pos().getY())
                && (context.level().isEmpty() || this.level.test(context.level().getAsInt()));
    }

    public boolean hasLevelBounds() {
        return !this.level.isAny();
    }

    public String describe() {
        List<String> parts = new ArrayList<>();
        if (!this.dimensions.isEmpty()) {
            parts.add("dimensions=" + this.dimensions);
        }
        if (!this.distance.isAny()) {
            parts.add("distance=" + this.distance);
        }
        if (!this.y.isAny()) {
            parts.add("y=" + this.y);
        }
        if (!this.level.isAny()) {
            parts.add("level=" + this.level);
        }
        return parts.isEmpty() ? "always" : String.join(", ", parts);
    }
}
