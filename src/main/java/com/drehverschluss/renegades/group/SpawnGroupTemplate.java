package com.drehverschluss.renegades.group;

import com.drehverschluss.renegades.equipment.EquipmentConditions;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * A spawn group template: when its leader spawns naturally and the conditions match, the members spawn around it.
 * A template without members (or only 0..0 members) makes the leader spawn alone.
 *
 * @param id the template file id, e.g. renegades:warrior_pair
 */
public record SpawnGroupTemplate(ResourceLocation id, EntityType<?> leader, int weight, EquipmentConditions conditions, List<Member> members) {
    public record Member(EntityType<?> entity, int min, int max) {
    }
}
