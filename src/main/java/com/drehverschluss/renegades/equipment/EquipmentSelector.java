package com.drehverschluss.renegades.equipment;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.level.RenegadeLevels;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * Picks one pool entry for a mob: weighted among the matching regular entries, otherwise among the matching fallbacks.
 */
public final class EquipmentSelector {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    private EquipmentSelector() {
    }

    /**
     * @param candidates number of matching entries in the group (regular or fallback) the entry was picked from
     */
    public record Selection(EquipmentPoolEntry entry, SpawnContext context, int candidates) {
    }

    /**
     * Equips a freshly spawned mob (called from finalizeSpawn). The mob keeps no equipment if no entry matches.
     */
    public static void equipOnSpawn(Mob mob, ServerLevel level) {
        OptionalInt mobLevel = RenegadeLevels.provider().map(provider -> provider.levelForSpawn(mob)).orElse(OptionalInt.empty());
        select(mob, level, mobLevel, mob.getRandom()).ifPresent(selection -> apply(mob, selection.entry(), mob.getRandom()));
    }

    /**
     * Rolls the equipment of a mob that is already in the world again (debug command); clears it if no entry matches.
     */
    public static Optional<Selection> reequip(Mob mob, ServerLevel level) {
        OptionalInt mobLevel = RenegadeLevels.provider().map(provider -> provider.currentLevel(mob)).orElse(OptionalInt.empty());
        Optional<Selection> selection = select(mob, level, mobLevel, mob.getRandom());
        if (selection.isPresent()) {
            apply(mob, selection.get().entry(), mob.getRandom());
        } else {
            for (EquipmentSlot slot : SLOTS) {
                mob.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        return selection;
    }

    /**
     * Current context of a mob as used for selection (debug output).
     */
    public static SpawnContext currentContext(Mob mob, ServerLevel level) {
        return SpawnContext.of(mob, level, RenegadeLevels.provider().map(provider -> provider.currentLevel(mob)).orElse(OptionalInt.empty()));
    }

    public static Optional<Selection> select(Mob mob, ServerLevel level, OptionalInt mobLevel, RandomSource random) {
        SpawnContext context = SpawnContext.of(mob, level, mobLevel);
        List<EquipmentPoolEntry> entries = EquipmentPools.get(mob.getType()).entries();

        Optional<Selection> selection = pick(entries, false, context, random);
        if (selection.isEmpty()) {
            selection = pick(entries, true, context, random);
        }

        if (selection.isEmpty()) {
            Renegades.LOGGER.debug("No equipment pool entry matches {} ({})", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), context.describe());
        }
        return selection;
    }

    /**
     * Replaces all hand and armor slots with the entry's equipment; slots the entry does not define are cleared.
     */
    public static void apply(Mob mob, EquipmentPoolEntry entry, RandomSource random) {
        for (EquipmentSlot slot : SLOTS) {
            ItemChoice choice = entry.equipment().get(slot);
            mob.setItemSlot(slot, choice == null ? ItemStack.EMPTY : choice.pick(random));
        }
    }

    private static Optional<Selection> pick(List<EquipmentPoolEntry> entries, boolean fallback, SpawnContext context, RandomSource random) {
        List<EquipmentPoolEntry> matching = entries.stream()
                .filter(entry -> entry.fallback() == fallback && entry.conditions().test(context))
                .toList();
        if (matching.isEmpty()) {
            return Optional.empty();
        }

        int roll = random.nextInt(matching.stream().mapToInt(EquipmentPoolEntry::weight).sum());
        for (EquipmentPoolEntry entry : matching) {
            roll -= entry.weight();
            if (roll < 0) {
                return Optional.of(new Selection(entry, context, matching.size()));
            }
        }
        throw new IllegalStateException("Weighted pick out of range");
    }
}
