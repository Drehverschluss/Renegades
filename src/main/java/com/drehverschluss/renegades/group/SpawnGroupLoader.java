package com.drehverschluss.renegades.group;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.entity.RenegadeClasses;
import com.drehverschluss.renegades.equipment.EquipmentConditions;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
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
 * Loads data/[namespace]/renegades/spawn_groups/**.json (one template per file). Files whose "neoforge:conditions"
 * fail are skipped; invalid files are skipped with a warning.
 */
public class SpawnGroupLoader extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "renegades/spawn_groups";
    private static final Set<String> FILE_KEYS = Set.of("leader", "weight", "conditions", "members", "neoforge:conditions");
    private static final Set<String> MEMBER_KEYS = Set.of("entity", "min", "max");
    private static final int MAX_MEMBERS_PER_ENTRY = 16;

    public SpawnGroupLoader() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        ConditionalOps<JsonElement> ops = makeConditionalOps();
        Map<ResourceLocation, SpawnGroupTemplate> templates = new LinkedHashMap<>();
        Map<ResourceLocation, EntityType<?>> unavailable = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
            ResourceLocation id = file.getKey();
            try {
                // Checked before the file's own conditions, so the debug command can name the missing class mod.
                EntityType<?> missing = unavailableClass(file.getValue());
                if (missing != null) {
                    Renegades.LOGGER.debug("Skipping spawn group {}: {} is not available", id, BuiltInRegistries.ENTITY_TYPE.getKey(missing));
                    unavailable.put(id, missing);
                    continue;
                }
                if (!ICondition.conditionsMatched(ops, file.getValue())) {
                    Renegades.LOGGER.debug("Skipping spawn group {}: conditions not met", id);
                    continue;
                }
                templates.put(id, parse(id, GsonHelper.convertToJsonObject(file.getValue(), "spawn group")));
            } catch (RuntimeException e) {
                Renegades.LOGGER.warn("Skipping spawn group file {}: {}", id, e.getMessage());
            }
        }

        SpawnGroups.set(templates, unavailable);

        Map<EntityType<?>, List<SpawnGroupTemplate>> byLeader = new LinkedHashMap<>();
        templates.values().forEach(template -> byLeader.computeIfAbsent(template.leader(), type -> new ArrayList<>()).add(template));
        byLeader.forEach((leader, list) -> Renegades.LOGGER.info("Spawn groups for {}: {} template(s) {}",
                BuiltInRegistries.ENTITY_TYPE.getKey(leader), list.size(), list.stream().map(SpawnGroupTemplate::id).toList()));
    }

    /**
     * The first renegade class in the file (leader or member) that is not available because its mods are missing (see
     * RenegadeClasses), or null.
     */
    @Nullable
    private static EntityType<?> unavailableClass(JsonElement json) {
        if (!json.isJsonObject()) {
            return null;
        }
        JsonObject object = json.getAsJsonObject();
        List<String> ids = new ArrayList<>();
        if (object.has("leader") && object.get("leader").isJsonPrimitive()) {
            ids.add(object.get("leader").getAsString());
        }
        if (object.has("members") && object.get("members").isJsonArray()) {
            for (JsonElement member : object.getAsJsonArray("members")) {
                if (member.isJsonObject() && member.getAsJsonObject().has("entity") && member.getAsJsonObject().get("entity").isJsonPrimitive()) {
                    ids.add(member.getAsJsonObject().get("entity").getAsString());
                }
            }
        }
        for (String value : ids) {
            ResourceLocation entityId = ResourceLocation.tryParse(value);
            EntityType<?> type = entityId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
            if (type != null && !RenegadeClasses.isAvailable(type)) {
                return type;
            }
        }
        return null;
    }

    private static SpawnGroupTemplate parse(ResourceLocation id, JsonObject json) {
        for (String key : json.keySet()) {
            if (!FILE_KEYS.contains(key)) {
                Renegades.LOGGER.warn("Ignoring unknown key '{}' in spawn group {}", key, id);
            }
        }

        EntityType<?> leader = entityType(GsonHelper.getAsString(json, "leader"));
        int weight = GsonHelper.getAsInt(json, "weight", 1);
        if (weight <= 0) {
            throw new JsonParseException("weight must be greater than 0, got " + weight);
        }
        EquipmentConditions conditions = json.has("conditions")
                ? EquipmentConditions.fromJson(GsonHelper.getAsJsonObject(json, "conditions"))
                : EquipmentConditions.NONE;

        List<SpawnGroupTemplate.Member> members = new ArrayList<>();
        if (json.has("members")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "members")) {
                members.add(parseMember(GsonHelper.convertToJsonObject(element, "member")));
            }
        }
        return new SpawnGroupTemplate(id, leader, weight, conditions, List.copyOf(members));
    }

    private static SpawnGroupTemplate.Member parseMember(JsonObject json) {
        for (String key : json.keySet()) {
            if (!MEMBER_KEYS.contains(key)) {
                throw new JsonParseException("Unknown member key '" + key + "', expected one of " + MEMBER_KEYS);
            }
        }

        EntityType<?> entity = entityType(GsonHelper.getAsString(json, "entity"));
        int min = GsonHelper.getAsInt(json, "min", 1);
        int max = GsonHelper.getAsInt(json, "max", min);
        if (min < 0 || max < min || max > MAX_MEMBERS_PER_ENTRY) {
            throw new JsonParseException("Invalid member count min=" + min + ", max=" + max + " (0 <= min <= max <= " + MAX_MEMBERS_PER_ENTRY + ")");
        }
        return new SpawnGroupTemplate.Member(entity, min, max);
    }

    private static EntityType<?> entityType(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new JsonParseException("Invalid entity id '" + value + "'");
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElseThrow(() -> new JsonParseException("Unknown entity '" + id + "'"));
    }
}
