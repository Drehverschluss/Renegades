package com.drehverschluss.renegades.equipment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

/**
 * Parses equipment pool files (and the item and slot values of rare item files). Invalid entries are skipped with a
 * warning; an invalid file header throws.
 */
final class EquipmentPoolParser {
    private static final Set<String> FILE_KEYS = Set.of("entity", "fallback", "entries", "neoforge:conditions");
    private static final Set<String> ENTRY_KEYS = Set.of("id", "weight", "conditions", "equipment", "variant");
    private static final Set<String> OPTION_KEYS = Set.of("item", "enchantments", "enchant_with_levels");
    static final Map<String, EquipmentSlot> SLOTS = Map.of(
            "head", EquipmentSlot.HEAD,
            "chest", EquipmentSlot.CHEST,
            "legs", EquipmentSlot.LEGS,
            "feet", EquipmentSlot.FEET,
            "mainhand", EquipmentSlot.MAINHAND,
            "offhand", EquipmentSlot.OFFHAND);

    private EquipmentPoolParser() {
    }

    record ParsedFile(EntityType<?> entityType, List<EquipmentPoolEntry> entries) {
    }

    static ParsedFile parseFile(ResourceLocation fileId, JsonObject json, Logger logger) {
        warnUnknownKeys(json, FILE_KEYS, "file " + fileId, logger);

        ResourceLocation entityId = parseId(GsonHelper.getAsString(json, "entity"), "entity");
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId)
                .orElseThrow(() -> new JsonParseException("Unknown entity '" + entityId + "'"));
        boolean fallback = GsonHelper.getAsBoolean(json, "fallback", false);
        JsonArray entriesJson = GsonHelper.getAsJsonArray(json, "entries");

        List<EquipmentPoolEntry> entries = new ArrayList<>();
        for (int i = 0; i < entriesJson.size(); i++) {
            JsonElement element = entriesJson.get(i);
            String defaultId = fileId + "#" + i;
            try {
                entries.add(parseEntry(fileId, defaultId, GsonHelper.convertToJsonObject(element, "entry"), fallback, logger));
            } catch (RuntimeException e) {
                String id = element.isJsonObject() ? GsonHelper.getAsString(element.getAsJsonObject(), "id", defaultId) : defaultId;
                logger.warn("Skipping equipment pool entry '{}' in {}: {}", id, fileId, e.getMessage());
            }
        }
        return new ParsedFile(entityType, entries);
    }

    private static EquipmentPoolEntry parseEntry(ResourceLocation fileId, String defaultId, JsonObject json, boolean fallback, Logger logger) {
        String id = GsonHelper.getAsString(json, "id", defaultId);
        warnUnknownKeys(json, ENTRY_KEYS, "entry '" + id + "' in " + fileId, logger);

        int weight = GsonHelper.getAsInt(json, "weight", 1);
        if (weight <= 0) {
            throw new JsonParseException("weight must be greater than 0, got " + weight);
        }

        EquipmentConditions conditions = json.has("conditions")
                ? EquipmentConditions.fromJson(GsonHelper.getAsJsonObject(json, "conditions"))
                : EquipmentConditions.NONE;

        JsonObject equipmentJson = GsonHelper.getAsJsonObject(json, "equipment");
        Map<EquipmentSlot, ItemChoice> equipment = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<String, JsonElement> slotEntry : equipmentJson.entrySet()) {
            EquipmentSlot slot = SLOTS.get(slotEntry.getKey());
            if (slot == null) {
                throw new JsonParseException("Unknown slot '" + slotEntry.getKey() + "', expected one of " + SLOTS.keySet());
            }
            equipment.put(slot, parseChoice(slotEntry.getValue(), slotEntry.getKey()));
        }

        String variant = json.has("variant") ? GsonHelper.getAsString(json, "variant") : null;
        return new EquipmentPoolEntry(id, fileId, fallback, weight, conditions, Map.copyOf(equipment), variant);
    }

    private static ItemChoice parseChoice(JsonElement json, String slotName) {
        return parseChoice(json, slotName, null);
    }

    /**
     * An item, #tag, object or a list of them. With a skipped list, options naming an unknown item (e.g. from a mod that
     * is not installed) are left out and noted there instead of failing; the choice may then be null (nothing left).
     */
    @Nullable
    static ItemChoice parseChoice(JsonElement json, String slotName, @Nullable List<String> skipped) {
        List<ItemChoice.Option> options = new ArrayList<>();
        for (JsonElement element : json.isJsonArray() ? json.getAsJsonArray() : List.of(json)) {
            try {
                options.add(parseOptionElement(element, slotName));
            } catch (UnknownItemException e) {
                if (skipped == null) {
                    throw e;
                }
                skipped.add(e.itemId.toString());
            }
        }

        if (options.isEmpty()) {
            if (skipped != null) {
                return null;
            }
            throw new JsonParseException("Slot '" + slotName + "' has an empty list");
        }
        return new ItemChoice(List.copyOf(options));
    }

    /**
     * An option is either an item id / #tag string or an object {"item": ..., "enchantments": {id: level},
     * "enchant_with_levels": {"min": .., "max": ..}}.
     */
    private static ItemChoice.Option parseOptionElement(JsonElement element, String slotName) {
        if (!element.isJsonObject()) {
            return parseOption(GsonHelper.convertToString(element, slotName), Enchanting.NONE);
        }
        JsonObject json = element.getAsJsonObject();
        for (String key : json.keySet()) {
            if (!OPTION_KEYS.contains(key)) {
                throw new JsonParseException("Unknown key '" + key + "' in item of slot '" + slotName + "', expected " + OPTION_KEYS);
            }
        }

        Map<ResourceLocation, Integer> fixed = new LinkedHashMap<>();
        if (json.has("enchantments")) {
            for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(json, "enchantments").entrySet()) {
                int level = GsonHelper.convertToInt(entry.getValue(), "enchantment level");
                if (level < 1 || level > 255) {
                    throw new JsonParseException("Enchantment level of '" + entry.getKey() + "' must be 1..255, got " + level);
                }
                fixed.put(parseId(entry.getKey(), "enchantment"), level);
            }
        }

        Enchanting.Range tableLevels = null;
        if (json.has("enchant_with_levels")) {
            JsonObject levels = GsonHelper.getAsJsonObject(json, "enchant_with_levels");
            int min = GsonHelper.getAsInt(levels, "min");
            int max = GsonHelper.getAsInt(levels, "max", min);
            if (min < 1 || max < min) {
                throw new JsonParseException("enchant_with_levels needs 1 <= min <= max, got " + min + ".." + max);
            }
            tableLevels = new Enchanting.Range(min, max);
        }

        return parseOption(GsonHelper.getAsString(json, "item"), new Enchanting(Map.copyOf(fixed), tableLevels));
    }

    private static ItemChoice.Option parseOption(String value, Enchanting enchanting) {
        if (value.startsWith("#")) {
            return new ItemChoice.TagOption(TagKey.create(Registries.ITEM, parseId(value.substring(1), "item tag")), enchanting);
        }

        ResourceLocation itemId = parseId(value, "item");
        Item item = BuiltInRegistries.ITEM.getOptional(itemId)
                .orElseThrow(() -> new UnknownItemException(itemId));
        return new ItemChoice.ItemOption(item, enchanting);
    }

    private static final class UnknownItemException extends JsonParseException {
        private final ResourceLocation itemId;

        UnknownItemException(ResourceLocation itemId) {
            super("Unknown item '" + itemId + "'");
            this.itemId = itemId;
        }
    }

    static ResourceLocation parseId(String value, String what) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new JsonParseException("Invalid " + what + " id '" + value + "'");
        }
        return id;
    }

    private static void warnUnknownKeys(JsonObject json, Set<String> known, String where, Logger logger) {
        for (String key : json.keySet()) {
            if (!known.contains(key)) {
                logger.warn("Ignoring unknown key '{}' in equipment pool {}", key, where);
            }
        }
    }
}
