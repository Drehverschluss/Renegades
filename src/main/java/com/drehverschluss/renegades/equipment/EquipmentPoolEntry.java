package com.drehverschluss.renegades.equipment;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * One complete equipment set; chosen as a whole on spawn.
 *
 * @param source the pool file this entry was loaded from
 */
public record EquipmentPoolEntry(String id, ResourceLocation source, boolean fallback, int weight,
                                 EquipmentConditions conditions, Map<EquipmentSlot, ItemChoice> equipment) {
}
