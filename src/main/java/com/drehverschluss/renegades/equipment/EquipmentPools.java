package com.drehverschluss.renegades.equipment;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.level.RenegadeLevels;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Loaded equipment pools, grouped by entity type. Replaced as a whole on every data reload.
 */
public final class EquipmentPools {
    private static final Pool EMPTY = new Pool(Set.of(), List.of());

    private static volatile Map<EntityType<?>, Pool> pools = Map.of();

    private EquipmentPools() {
    }

    /**
     * @param files pool files for this entity (including files whose entries were all skipped)
     */
    public record Pool(Set<ResourceLocation> files, List<EquipmentPoolEntry> entries) {
        public long fallbackCount() {
            return this.entries.stream().filter(EquipmentPoolEntry::fallback).count();
        }
    }

    public static Pool get(EntityType<?> entityType) {
        return pools.getOrDefault(entityType, EMPTY);
    }

    public static Set<EntityType<?>> entityTypes() {
        return pools.keySet();
    }

    static void set(Map<EntityType<?>, Pool> loaded) {
        pools = Map.copyOf(loaded);
    }

    /**
     * Item tags are bound only after all reload listeners ran, so empty tags are checked afterwards (TagsUpdatedEvent).
     * Tag options without items are removed; an entry with a slot that has no option left is skipped.
     */
    public static void validateTagsAndLog() {
        Map<EntityType<?>, Pool> validated = new LinkedHashMap<>();
        boolean usesLevelBounds = false;

        for (Map.Entry<EntityType<?>, Pool> poolEntry : pools.entrySet()) {
            List<EquipmentPoolEntry> entries = new ArrayList<>();
            for (EquipmentPoolEntry entry : poolEntry.getValue().entries()) {
                EquipmentPoolEntry checked = withoutEmptyTags(entry);
                if (checked != null) {
                    entries.add(checked);
                    usesLevelBounds |= checked.conditions().hasLevelBounds();
                }
            }

            Pool pool = new Pool(poolEntry.getValue().files(), List.copyOf(entries));
            validated.put(poolEntry.getKey(), pool);
            Renegades.LOGGER.info("Equipment pools for {}: {} file(s), {} entries ({} fallback)",
                    BuiltInRegistries.ENTITY_TYPE.getKey(poolEntry.getKey()), pool.files().size(), entries.size(), pool.fallbackCount());
        }

        pools = Map.copyOf(validated);

        if (usesLevelBounds && RenegadeLevels.provider().isEmpty()) {
            Renegades.LOGGER.info("Some equipment pools use min_level/max_level, but no level source (e.g. Dynamic Difficulty) is installed; these bounds are ignored");
        }
    }

    @Nullable
    private static EquipmentPoolEntry withoutEmptyTags(EquipmentPoolEntry entry) {
        Map<EquipmentSlot, ItemChoice> equipment = new LinkedHashMap<>();

        for (Map.Entry<EquipmentSlot, ItemChoice> slot : entry.equipment().entrySet()) {
            List<ItemChoice.Option> options = new ArrayList<>();
            for (ItemChoice.Option option : slot.getValue().options()) {
                if (option instanceof ItemChoice.TagOption tagOption && tagOption.isEmpty()) {
                    Renegades.LOGGER.warn("Equipment pool entry '{}' in {}: item tag {} is empty or unknown", entry.id(), entry.source(), tagOption);
                } else {
                    options.add(option);
                }
            }

            if (options.isEmpty()) {
                Renegades.LOGGER.warn("Skipping equipment pool entry '{}' in {}: slot '{}' has no usable item", entry.id(), entry.source(), slot.getKey().getName());
                return null;
            }
            equipment.put(slot.getKey(), new ItemChoice(List.copyOf(options)));
        }

        return new EquipmentPoolEntry(entry.id(), entry.source(), entry.fallback(), entry.weight(), entry.conditions(), Map.copyOf(equipment));
    }
}
