package com.drehverschluss.renegades.equipment;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.config.RenegadesConfig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;

/**
 * Loaded rare items, grouped by entity type. Replaced as a whole on every data reload.
 */
public final class RareItems {
    private static volatile List<RareItemEntry> loaded = List.of();
    private static volatile Map<EntityType<?>, List<RareItemEntry>> byType = Map.of();

    private RareItems() {
    }

    /**
     * Slots that got a rare item on this roll, and the ones among them whose item must not get affixes.
     */
    public record Result(Set<EquipmentSlot> replaced, Set<EquipmentSlot> noAffixes) {
        public static final Result NONE = new Result(Set.of(), Set.of());
    }

    public static List<RareItemEntry> get(EntityType<?> entityType) {
        return byType.getOrDefault(entityType, List.of());
    }

    public static Set<EntityType<?>> entityTypes() {
        return byType.keySet();
    }

    static void set(List<RareItemEntry> entries) {
        loaded = List.copyOf(entries);
        byType = group(loaded);
    }

    /**
     * Item tags are bound only after all reload listeners ran: tag options without items are removed (an entry without
     * any item left is dropped), then the entries per class are logged.
     */
    public static void validateTagsAndLog(RegistryAccess registries) {
        List<RareItemEntry> validated = new ArrayList<>();
        for (RareItemEntry entry : loaded) {
            ItemChoice items = withoutEmptyTags(entry.items(), entry, "items", registries);
            ItemChoice replaces = entry.replaces() == null ? null : withoutEmptyTags(entry.replaces(), entry, "replaces", registries);
            if (items == null || (entry.replaces() != null && replaces == null)) {
                Renegades.LOGGER.warn("Skipping rare item {}: no usable item in '{}'", entry.id(), items == null ? "items" : "replaces");
                continue;
            }
            validated.add(entry.withItems(items, replaces));
        }
        loaded = List.copyOf(validated);
        byType = group(loaded);

        Set<EntityType<?>> types = new LinkedHashSet<>(EquipmentPools.entityTypes());
        types.addAll(byType.keySet());
        for (EntityType<?> type : types) {
            Renegades.LOGGER.info("Rare items for {}: {} entries", BuiltInRegistries.ENTITY_TYPE.getKey(type), get(type).size());
        }
    }

    /**
     * Rolls every rare item entry of the mob's type whose slot holds a matching piece and whose conditions are met,
     * once with chance x rareItemChanceMultiplier; at most one rare item per slot. Never fills an empty slot.
     */
    public static Result roll(Mob mob, SpawnContext context, RandomSource random) {
        List<RareItemEntry> entries = get(mob.getType());
        double multiplier = RenegadesConfig.RARE_ITEM_CHANCE_MULTIPLIER.get();
        if (entries.isEmpty() || multiplier <= 0.0D) {
            return Result.NONE;
        }

        Set<EquipmentSlot> replaced = EnumSet.noneOf(EquipmentSlot.class);
        Set<EquipmentSlot> noAffixes = EnumSet.noneOf(EquipmentSlot.class);
        for (RareItemEntry entry : entries) {
            if (replaced.contains(entry.slot()) || !entry.canReplace(mob.getItemBySlot(entry.slot())) || !entry.conditions().test(context)
                    || random.nextDouble() >= entry.chance() * multiplier) {
                continue;
            }
            mob.setItemSlot(entry.slot(), entry.items().pick(random, mob.level().registryAccess()));
            replaced.add(entry.slot());
            if (!entry.allowAffixes()) {
                noAffixes.add(entry.slot());
            }
            Renegades.LOGGER.debug("{} got rare item {} from {}", mob, BuiltInRegistries.ITEM.getKey(mob.getItemBySlot(entry.slot()).getItem()), entry.id());
        }
        return new Result(Set.copyOf(replaced), Set.copyOf(noAffixes));
    }

    private static Map<EntityType<?>, List<RareItemEntry>> group(List<RareItemEntry> entries) {
        Map<EntityType<?>, List<RareItemEntry>> grouped = new LinkedHashMap<>();
        for (RareItemEntry entry : entries) {
            for (EntityType<?> type : entry.entities()) {
                grouped.computeIfAbsent(type, key -> new ArrayList<>()).add(entry);
            }
        }
        Map<EntityType<?>, List<RareItemEntry>> copy = new LinkedHashMap<>();
        grouped.forEach((type, list) -> copy.put(type, List.copyOf(list)));
        return Map.copyOf(copy);
    }

    @Nullable
    private static ItemChoice withoutEmptyTags(ItemChoice choice, RareItemEntry entry, String field, RegistryAccess registries) {
        List<ItemChoice.Option> options = new ArrayList<>();
        for (ItemChoice.Option option : choice.options()) {
            if (option instanceof ItemChoice.TagOption tagOption && tagOption.isEmpty()) {
                Renegades.LOGGER.warn("Rare item {}: item tag {} in '{}' is empty or unknown", entry.id(), tagOption, field);
                continue;
            }
            List<ResourceLocation> unknown = option.enchanting().unknownIds(registries);
            if (!unknown.isEmpty()) {
                Renegades.LOGGER.warn("Rare item {}: unknown enchantment(s) {} are ignored", entry.id(), unknown);
                option = option.withEnchanting(option.enchanting().withoutIds(unknown));
            }
            options.add(option);
        }
        return options.isEmpty() ? null : new ItemChoice(List.copyOf(options));
    }
}
