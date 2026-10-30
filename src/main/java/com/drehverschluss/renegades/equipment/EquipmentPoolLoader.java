package com.drehverschluss.renegades.equipment;

import com.drehverschluss.renegades.Renegades;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Loads data/[namespace]/renegades/equipment_pools/**.json. Files whose "neoforge:conditions" fail are skipped.
 */
public class EquipmentPoolLoader extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "renegades/equipment_pools";

    public EquipmentPoolLoader() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        ConditionalOps<JsonElement> ops = makeConditionalOps();
        Map<EntityType<?>, Set<ResourceLocation>> filesByType = new LinkedHashMap<>();
        Map<EntityType<?>, List<EquipmentPoolEntry>> entriesByType = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
            ResourceLocation fileId = file.getKey();
            try {
                if (!ICondition.conditionsMatched(ops, file.getValue())) {
                    Renegades.LOGGER.debug("Skipping equipment pool {}: conditions not met", fileId);
                    continue;
                }

                EquipmentPoolParser.ParsedFile parsed = EquipmentPoolParser.parseFile(fileId,
                        GsonHelper.convertToJsonObject(file.getValue(), "equipment pool"), Renegades.LOGGER);
                filesByType.computeIfAbsent(parsed.entityType(), type -> new LinkedHashSet<>()).add(fileId);
                entriesByType.computeIfAbsent(parsed.entityType(), type -> new ArrayList<>()).addAll(parsed.entries());
            } catch (RuntimeException e) {
                Renegades.LOGGER.warn("Skipping equipment pool file {}: {}", fileId, e.getMessage());
            }
        }

        Map<EntityType<?>, EquipmentPools.Pool> pools = new LinkedHashMap<>();
        filesByType.forEach((type, typeFiles) -> pools.put(type,
                new EquipmentPools.Pool(Set.copyOf(typeFiles), List.copyOf(entriesByType.get(type)))));
        EquipmentPools.set(pools);
    }
}
