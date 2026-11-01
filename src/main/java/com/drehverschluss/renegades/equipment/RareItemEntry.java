package com.drehverschluss.renegades.equipment;

import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * A rare item (e.g. a unique weapon) that very rarely replaces a piece of a renegade's pool equipment on spawn.
 * Loaded from data/[namespace]/renegades/rare_items/*.json, one entry per file.
 *
 * @param replaces     only pieces matching this are replaced; null = any worn piece in the slot
 * @param allowAffixes false = the elite upgrade (Apotheosis) never gives this item affixes
 */
public record RareItemEntry(ResourceLocation id, Set<EntityType<?>> entities, EquipmentSlot slot, double chance, ItemChoice items,
                            @Nullable ItemChoice replaces, EquipmentConditions conditions, boolean allowAffixes) {
    public boolean canReplace(ItemStack worn) {
        return !worn.isEmpty() && (this.replaces == null || this.replaces.matches(worn));
    }

    public RareItemEntry withItems(ItemChoice newItems, @Nullable ItemChoice newReplaces) {
        return new RareItemEntry(this.id, this.entities, this.slot, this.chance, newItems, newReplaces, this.conditions, this.allowAffixes);
    }
}
