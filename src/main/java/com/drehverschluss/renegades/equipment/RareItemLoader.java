package com.drehverschluss.renegades.equipment;

import com.drehverschluss.renegades.Renegades;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Loads data/[namespace]/renegades/rare_items/**.json (one entry per file; a file with the same path in a later pack
 * replaces it). Files whose "neoforge:conditions" fail are skipped; items of mods that are not installed are left out,
 * and an entry without any item left is dropped. Items and conditions are read like in the equipment pools.
 */
public class RareItemLoader extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "renegades/rare_items";
    private static final Set<String> KEYS = Set.of("entity", "slot", "chance", "items", "replaces", "conditions", "allow_affixes",
            "neoforge:conditions");

    public RareItemLoader() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        ConditionalOps<JsonElement> ops = makeConditionalOps();
        List<RareItemEntry> entries = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
            ResourceLocation fileId = file.getKey();
            try {
                if (!ICondition.conditionsMatched(ops, file.getValue())) {
                    Renegades.LOGGER.debug("Skipping rare item {}: conditions not met", fileId);
                    continue;
                }
                RareItemEntry entry = parse(fileId, GsonHelper.convertToJsonObject(file.getValue(), "rare item"));
                if (entry != null) {
                    entries.add(entry);
                }
            } catch (RuntimeException e) {
                Renegades.LOGGER.warn("Skipping rare item file {}: {}", fileId, e.getMessage());
            }
        }
        RareItems.set(entries);
    }

    private static RareItemEntry parse(ResourceLocation fileId, JsonObject json) {
        for (String key : json.keySet()) {
            if (!KEYS.contains(key)) {
                Renegades.LOGGER.warn("Ignoring unknown key '{}' in rare item {}", key, fileId);
            }
        }

        Set<EntityType<?>> entities = new LinkedHashSet<>();
        JsonElement entityJson = json.get("entity");
        if (entityJson == null) {
            throw new JsonParseException("Missing 'entity'");
        }
        for (JsonElement element : entityJson.isJsonArray() ? entityJson.getAsJsonArray() : List.of(entityJson)) {
            ResourceLocation entityId = EquipmentPoolParser.parseId(GsonHelper.convertToString(element, "entity"), "entity");
            BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).ifPresentOrElse(entities::add,
                    () -> Renegades.LOGGER.warn("Rare item {}: unknown entity '{}' is ignored", fileId, entityId));
        }
        if (entities.isEmpty()) {
            throw new JsonParseException("No known entity");
        }

        String slotName = GsonHelper.getAsString(json, "slot");
        EquipmentSlot slot = EquipmentPoolParser.SLOTS.get(slotName);
        if (slot == null) {
            throw new JsonParseException("Unknown slot '" + slotName + "', expected one of " + EquipmentPoolParser.SLOTS.keySet());
        }

        double chance = GsonHelper.getAsDouble(json, "chance");
        if (chance < 0.0D || chance > 1.0D) {
            throw new JsonParseException("chance must be 0..1, got " + chance);
        }

        List<String> skipped = new ArrayList<>();
        ItemChoice items = EquipmentPoolParser.parseChoice(GsonHelper.getNonNull(json, "items"), "items", skipped);
        if (!skipped.isEmpty()) {
            Renegades.LOGGER.info("Rare item {}: unknown item(s) {} left out", fileId, skipped);
        }
        if (items == null) {
            Renegades.LOGGER.info("Rare item {} dropped: none of its items exist", fileId);
            return null;
        }

        ItemChoice replaces = null;
        if (json.has("replaces")) {
            List<String> skippedReplaces = new ArrayList<>();
            replaces = EquipmentPoolParser.parseChoice(json.get("replaces"), "replaces", skippedReplaces);
            if (replaces == null) {
                Renegades.LOGGER.info("Rare item {} dropped: none of the items in 'replaces' exist", fileId);
                return null;
            }
        }

        EquipmentConditions conditions = json.has("conditions")
                ? EquipmentConditions.fromJson(GsonHelper.getAsJsonObject(json, "conditions"))
                : EquipmentConditions.NONE;
        boolean allowAffixes = GsonHelper.getAsBoolean(json, "allow_affixes", true);
        return new RareItemEntry(fileId, Set.copyOf(entities), slot, chance, items, replaces, conditions, allowAffixes);
    }
}
