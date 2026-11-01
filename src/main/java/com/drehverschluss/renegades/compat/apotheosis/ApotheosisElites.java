package com.drehverschluss.renegades.compat.apotheosis;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.compat.apotheosis.ApotheosisCompat.AugmentItem;
import com.drehverschluss.renegades.compat.apotheosis.ApotheosisCompat.EliteResult;
import com.drehverschluss.renegades.compat.apotheosis.ApotheosisCompat.ForcedElite;
import com.drehverschluss.renegades.compat.apotheosis.ApotheosisCompat.Upgraded;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Elite renegades with Apotheosis. Only loaded when Apotheosis is installed (see ApotheosisCompat).
 * <p>
 * Apotheosis' random_affix_item augment puts a random affix item into a slot of a spawning monster. For renegades our
 * pool equipment replaces that item right after, so instead one of the renegade's own pieces gets affixes and the
 * rarity of that item. Making a renegade an elite has two steps: {@link #planAugments} / {@link #planFullSet} decide
 * which pieces get which rarity, {@link #upgradePart} upgrades one piece. Further modes only need another plan.
 */
final class ApotheosisElites {
    private static final EquipmentSlot[] GEAR_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private ApotheosisElites() {
    }

    private record Upgrade(EquipmentSlot slot, LootRarity rarity) {
    }

    static List<AugmentItem> findAugmentItems(Mob mob) {
        List<AugmentItem> found = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty() || !Boolean.TRUE.equals(stack.get(Apoth.Components.FROM_MOB))) {
                continue;
            }
            DynamicHolder<LootRarity> rarity = AffixHelper.getRarity(stack);
            if (rarity.isBound()) {
                found.add(new AugmentItem(slot, rarity.getId()));
            }
        }
        return found;
    }

    static EliteResult applyEliteGear(RenegadeEntity renegade, ServerLevel level, MobSpawnType spawnType, List<AugmentItem> augmentItems,
                                      @Nullable ForcedElite forced) {
        // Same generation context as Apotheosis' augments: the nearest player (world tier, luck) at the mob's position.
        Player player = level.getNearestPlayer(renegade.getX(), renegade.getY(), renegade.getZ(), -1.0D, false);
        if (player == null) {
            if (!augmentItems.isEmpty() || forced != null) {
                Renegades.LOGGER.debug("No player near {}, no elite gear", renegade);
            }
            return EliteResult.NONE;
        }
        RandomSource random = level.getRandom();
        GenContext context = GenContext.forPlayerAtPos(random, player, renegade.blockPosition());

        List<Upgrade> plan;
        boolean fullSet;
        if (forced != null) {
            LootRarity rarity = RarityRegistry.INSTANCE.getValue(forced.rarity());
            if (rarity == null) {
                return EliteResult.NONE;
            }
            fullSet = forced.fullSet();
            plan = fullSet ? planFullSet(renegade, rarity) : planSinglePiece(renegade, rarity, random);
        } else if (spawnType != MobSpawnType.SPAWNER && spawnType != MobSpawnType.TRIAL_SPAWNER
                && random.nextDouble() < RenegadesConfig.FULL_SET_ELITE_CHANCE.get()) {
            // The elite set replaces the augment items: no piece is upgraded twice.
            fullSet = true;
            plan = planFullSet(renegade, LootRarity.random(context));
        } else {
            fullSet = false;
            plan = planAugments(renegade, augmentItems, random);
        }

        List<Upgraded> upgraded = new ArrayList<>();
        LootRarity highest = null;
        for (Upgrade upgrade : plan) {
            int affixes = upgradePart(renegade, upgrade.slot(), upgrade.rarity(), context);
            upgraded.add(new Upgraded(upgrade.slot(), RarityRegistry.INSTANCE.getKey(upgrade.rarity()),
                    BuiltInRegistries.ITEM.getKey(renegade.getItemBySlot(upgrade.slot()).getItem()), affixes));
            if (highest == null || upgrade.rarity().sortIndex() > highest.sortIndex()) {
                highest = upgrade.rarity();
            }
        }
        if (highest == null) {
            return EliteResult.NONE;
        }
        renegade.setEliteState(RarityRegistry.INSTANCE.getKey(highest), highest.color().getValue(), fullSet);
        return new EliteResult(List.copyOf(upgraded), fullSet);
    }

    /**
     * One own piece per augment item, each piece at most once: the piece in the item's slot if it can take affixes,
     * otherwise a random other one; augment items without a free piece are left out.
     */
    private static List<Upgrade> planAugments(RenegadeEntity renegade, List<AugmentItem> augmentItems, RandomSource random) {
        List<Upgrade> plan = new ArrayList<>();
        Set<EquipmentSlot> used = EnumSet.noneOf(EquipmentSlot.class);
        for (AugmentItem item : augmentItems) {
            LootRarity rarity = RarityRegistry.INSTANCE.getValue(item.rarity());
            if (rarity == null) {
                continue;
            }
            EquipmentSlot slot = canTakeAffixes(renegade, item.slot()) && !used.contains(item.slot())
                    ? item.slot()
                    : randomFreePiece(renegade, used, random);
            if (slot == null) {
                Renegades.LOGGER.debug("No piece of {} left for the augment item in {} ({})", renegade, item.slot().getName(), item.rarity());
                continue;
            }
            used.add(slot);
            plan.add(new Upgrade(slot, rarity));
        }
        return plan;
    }

    /**
     * Every worn piece that can take affixes, all with the same rarity.
     */
    private static List<Upgrade> planFullSet(RenegadeEntity renegade, LootRarity rarity) {
        List<Upgrade> plan = new ArrayList<>();
        for (EquipmentSlot slot : GEAR_SLOTS) {
            if (canTakeAffixes(renegade, slot)) {
                plan.add(new Upgrade(slot, rarity));
            }
        }
        return plan;
    }

    private static List<Upgrade> planSinglePiece(RenegadeEntity renegade, LootRarity rarity, RandomSource random) {
        EquipmentSlot slot = randomFreePiece(renegade, EnumSet.noneOf(EquipmentSlot.class), random);
        return slot == null ? List.of() : List.of(new Upgrade(slot, rarity));
    }

    @Nullable
    private static EquipmentSlot randomFreePiece(RenegadeEntity renegade, Set<EquipmentSlot> used, RandomSource random) {
        List<EquipmentSlot> free = new ArrayList<>();
        for (EquipmentSlot slot : GEAR_SLOTS) {
            if (!used.contains(slot) && canTakeAffixes(renegade, slot)) {
                free.add(slot);
            }
        }
        return free.isEmpty() ? null : free.get(random.nextInt(free.size()));
    }

    private static boolean canTakeAffixes(RenegadeEntity renegade, EquipmentSlot slot) {
        ItemStack stack = renegade.getItemBySlot(slot);
        return !stack.isEmpty() && !AffixHelper.hasAffixes(stack) && !LootCategory.forItem(stack).isNone();
    }

    /**
     * Gives one worn piece affixes and the rarity like an Apotheosis loot item (rarity, the category's loot rules,
     * affix name). Returns the number of affixes it got. The drop chance stays ours (see resetVanillaDropChances), so
     * the piece drops through the normal renegade drop rules with its affixes.
     */
    private static int upgradePart(RenegadeEntity renegade, EquipmentSlot slot, LootRarity rarity, GenContext context) {
        ItemStack stack = renegade.getItemBySlot(slot);
        LootController.createLootItem(stack, LootCategory.forItem(stack), rarity, context);
        // Marks the item as coming from a mob, like Apotheosis' own augment items. Apotheosis 8.9.0 only sets this
        // persistent component and reads it nowhere; kept so packs and other mods can tell mob gear apart.
        stack.set(Apoth.Components.FROM_MOB, true);
        renegade.setItemSlot(slot, stack);
        return AffixHelper.getAffixes(stack).size();
    }

    static OptionalInt rarityColor(ResourceLocation id) {
        LootRarity rarity = RarityRegistry.INSTANCE.getValue(id);
        return rarity == null ? OptionalInt.empty() : OptionalInt.of(rarity.color().getValue());
    }

    static List<ResourceLocation> rarityIds() {
        return RarityRegistry.getSortedRarities().stream()
                .sorted(Comparator.comparingInt(LootRarity::sortIndex))
                .map(RarityRegistry.INSTANCE::getKey)
                .toList();
    }
}
