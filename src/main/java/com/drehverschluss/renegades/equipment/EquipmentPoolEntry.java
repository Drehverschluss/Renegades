package com.drehverschluss.renegades.equipment;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * One complete equipment set; chosen as a whole on spawn.
 *
 * @param source  the pool file this entry was loaded from
 * @param variant optional class variant this set belongs to (e.g. "fire" for a fire wizard robe); null = random
 */
public record EquipmentPoolEntry(String id, ResourceLocation source, boolean fallback, int weight,
                                 EquipmentConditions conditions, Map<EquipmentSlot, ItemChoice> equipment, @Nullable String variant) {
    public EquipmentPoolEntry withEquipment(Map<EquipmentSlot, ItemChoice> newEquipment) {
        return new EquipmentPoolEntry(this.id, this.source, this.fallback, this.weight, this.conditions, newEquipment, this.variant);
    }
}