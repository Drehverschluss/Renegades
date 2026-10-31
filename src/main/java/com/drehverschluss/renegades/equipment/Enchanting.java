package com.drehverschluss.renegades.equipment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Enchantments of a pool item: fixed enchantments with levels and/or random ones like from an enchanting table with a
 * level between min and max (only enchantments the table offers for this item, no treasure enchantments).
 */
public record Enchanting(Map<ResourceLocation, Integer> fixed, @Nullable Range tableLevels) {
    public static final Enchanting NONE = new Enchanting(Map.of(), null);

    public record Range(int min, int max) {
        int roll(RandomSource random) {
            return Mth.randomBetweenInclusive(random, this.min, this.max);
        }
    }

    public boolean isNone() {
        return this.fixed.isEmpty() && this.tableLevels == null;
    }

    public ItemStack apply(ItemStack stack, RandomSource random, RegistryAccess registries) {
        if (stack.isEmpty() || this.isNone()) {
            return stack;
        }
        Registry<Enchantment> enchantments = registries.registryOrThrow(Registries.ENCHANTMENT);
        for (Map.Entry<ResourceLocation, Integer> entry : this.fixed.entrySet()) {
            enchantments.getHolder(ResourceKey.create(Registries.ENCHANTMENT, entry.getKey()))
                    .ifPresent(holder -> stack.enchant(holder, entry.getValue()));
        }
        if (this.tableLevels != null) {
            return EnchantmentHelper.enchantItem(random, stack, this.tableLevels.roll(random), registries,
                    enchantments.getTag(EnchantmentTags.IN_ENCHANTING_TABLE));
        }
        return stack;
    }

    /**
     * Fixed enchantment ids that do not exist in the given registries.
     */
    public List<ResourceLocation> unknownIds(RegistryAccess registries) {
        Registry<Enchantment> enchantments = registries.registryOrThrow(Registries.ENCHANTMENT);
        List<ResourceLocation> unknown = new ArrayList<>();
        for (ResourceLocation id : this.fixed.keySet()) {
            if (!enchantments.containsKey(id)) {
                unknown.add(id);
            }
        }
        return unknown;
    }

    public Enchanting withoutIds(List<ResourceLocation> ids) {
        Map<ResourceLocation, Integer> kept = this.fixed.entrySet().stream()
                .filter(entry -> !ids.contains(entry.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new Enchanting(kept, this.tableLevels);
    }

    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        this.fixed.forEach((id, level) -> parts.add(id + " " + level));
        if (this.tableLevels != null) {
            parts.add("random level " + this.tableLevels.min() + ".." + this.tableLevels.max());
        }
        return "{" + String.join(", ", parts) + "}";
    }
}
