package com.drehverschluss.renegades.equipment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Optional spawn conditions (used by equipment pool entries and spawn groups); all given conditions must match.
 * Ranges are inclusive. Level bounds only apply when the mob has a level (see RenegadeLevels); otherwise they count as met.
 */
public record EquipmentConditions(Set<ResourceLocation> dimensions, IntBounds distance, IntBounds y, IntBounds level) {
    public static final EquipmentConditions NONE = new EquipmentConditions(Set.of(), IntBounds.ANY, IntBounds.ANY, IntBounds.ANY);
    private static final Set<String> KEYS = Set.of("dimensions", "min_distance", "max_distance", "min_y", "max_y", "min_level", "max_level");

    public static EquipmentConditions fromJson(JsonObject json) {
        for (String key : json.keySet()) {
            if (!KEYS.contains(key)) {
                throw new JsonParseException("Unknown condition '" + key + "', expected one of " + KEYS);
            }
        }

        Set<ResourceLocation> dimensions = new HashSet<>();
        if (json.has("dimensions")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "dimensions")) {
                String value = GsonHelper.convertToString(element, "dimension");
                ResourceLocation dimension = ResourceLocation.tryParse(value);
                if (dimension == null) {
                    throw new JsonParseException("Invalid dimension id '" + value + "'");
                }
                dimensions.add(dimension);
            }
        }

        return new EquipmentConditions(Set.copyOf(dimensions),
                bounds(json, "min_distance", "max_distance"),
                bounds(json, "min_y", "max_y"),
                bounds(json, "min_level", "max_level"));
    }

    private static IntBounds bounds(JsonObject json, String minKey, String maxKey) {
        Integer min = json.has(minKey) ? GsonHelper.getAsInt(json, minKey) : null;
        Integer max = json.has(maxKey) ? GsonHelper.getAsInt(json, maxKey) : null;
        if (min != null && max != null && min > max) {
            throw new JsonParseException(minKey + " (" + min + ") is greater than " + maxKey + " (" + max + ")");
        }
        return min == null && max == null ? IntBounds.ANY : new IntBounds(min, max);
    }

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
