package com.drehverschluss.renegades.visual;

import com.drehverschluss.renegades.Renegades;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Loads data/[namespace]/renegades/caster_visuals/**.json: which spell lends its look to which ability of a class.
 * A file with the same path in a later pack replaces it; several files for one class are merged per ability (files in
 * id order, later ones win). Files whose "neoforge:conditions" fail are skipped. Spell ids are only checked when used,
 * since the spells are loaded by another mod.
 */
public class CasterVisualsLoader extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "renegades/caster_visuals";
    private static final Set<String> FILE_KEYS = Set.of("entity", "abilities", "neoforge:conditions");
    private static final Set<String> CHOICE_KEYS = Set.of("spell", "variants", "model", "particles", "sounds");

    public CasterVisualsLoader() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        ConditionalOps<JsonElement> ops = makeConditionalOps();
        Map<EntityType<?>, Map<String, CasterVisualEntry>> loaded = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
            ResourceLocation fileId = file.getKey();
            try {
                if (!ICondition.conditionsMatched(ops, file.getValue())) {
                    Renegades.LOGGER.debug("Skipping caster visuals {}: conditions not met", fileId);
                    continue;
                }
                JsonObject json = GsonHelper.convertToJsonObject(file.getValue(), "caster visuals");
                for (String key : json.keySet()) {
                    if (!FILE_KEYS.contains(key)) {
                        Renegades.LOGGER.warn("Ignoring unknown key '{}' in caster visuals {}", key, fileId);
                    }
                }
                Map<String, CasterVisualEntry> abilities = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> ability : GsonHelper.getAsJsonObject(json, "abilities").entrySet()) {
                    try {
                        abilities.put(ability.getKey(), parseEntry(ability.getValue(), fileId, ability.getKey()));
                    } catch (RuntimeException e) {
                        Renegades.LOGGER.warn("Skipping ability '{}' in caster visuals {}: {}", ability.getKey(), fileId, e.getMessage());
                    }
                }
                JsonElement entityJson = GsonHelper.getNonNull(json, "entity");
                for (JsonElement element : entityJson.isJsonArray() ? entityJson.getAsJsonArray() : List.of(entityJson)) {
                    ResourceLocation entityId = id(GsonHelper.convertToString(element, "entity"), "entity");
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
                    if (type == null) {
                        Renegades.LOGGER.warn("Caster visuals {}: unknown entity '{}' is ignored", fileId, entityId);
                        continue;
                    }
                    loaded.computeIfAbsent(type, t -> new LinkedHashMap<>()).putAll(abilities);
                }
            } catch (RuntimeException e) {
                Renegades.LOGGER.warn("Skipping caster visuals file {}: {}", fileId, e.getMessage());
            }
        }

        CasterVisuals.set(loaded);
        loaded.forEach((type, abilities) -> Renegades.LOGGER.info("Caster visuals for {}: {} abilities",
                BuiltInRegistries.ENTITY_TYPE.getKey(type), abilities.size()));
    }

    /**
     * An ability is a spell id, or {"spell": ..., "variants": {variant: spell id or {...}}, "model"/"particles"/"sounds": bool}.
     */
    private static CasterVisualEntry parseEntry(JsonElement json, ResourceLocation fileId, String ability) {
        if (!json.isJsonObject()) {
            return new CasterVisualEntry(new CasterVisualEntry.Choice(id(GsonHelper.convertToString(json, ability), "spell"), null, null, null), Map.of());
        }
        JsonObject object = json.getAsJsonObject();
        for (String key : object.keySet()) {
            if (!CHOICE_KEYS.contains(key)) {
                Renegades.LOGGER.warn("Ignoring unknown key '{}' in ability '{}' of caster visuals {}", key, ability, fileId);
            }
        }
        Map<String, CasterVisualEntry.Choice> variants = new LinkedHashMap<>();
        if (object.has("variants")) {
            for (Map.Entry<String, JsonElement> variant : GsonHelper.getAsJsonObject(object, "variants").entrySet()) {
                variants.put(variant.getKey(), parseChoice(variant.getValue(), variant.getKey()));
            }
        }
        CasterVisualEntry.Choice base = parseChoice(object, ability);
        if (base.spell() == null && variants.isEmpty()) {
            throw new JsonParseException("needs a 'spell' or 'variants'");
        }
        return new CasterVisualEntry(base, Map.copyOf(variants));
    }

    private static CasterVisualEntry.Choice parseChoice(JsonElement json, String where) {
        if (!json.isJsonObject()) {
            return new CasterVisualEntry.Choice(id(GsonHelper.convertToString(json, where), "spell"), null, null, null);
        }
        JsonObject object = json.getAsJsonObject();
        ResourceLocation spell = object.has("spell") ? id(GsonHelper.getAsString(object, "spell"), "spell") : null;
        return new CasterVisualEntry.Choice(spell, optionalBoolean(object, "model"), optionalBoolean(object, "particles"), optionalBoolean(object, "sounds"));
    }

    @Nullable
    private static Boolean optionalBoolean(JsonObject object, String key) {
        return object.has(key) ? GsonHelper.getAsBoolean(object, key) : null;
    }

    private static ResourceLocation id(String value, String what) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new JsonParseException("Invalid " + what + " id '" + value + "'");
        }
        return id;
    }
}
