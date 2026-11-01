package com.drehverschluss.renegades.compat.apotheosis;

import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import java.util.List;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.fml.ModList;

/**
 * Entry point for the optional Apotheosis integration (elite renegades). Must not reference any Apotheosis class, so the
 * mod runs without it; the API is only touched in ApotheosisElites, which is loaded when Apotheosis is present.
 */
public final class ApotheosisCompat {
    public static final String MOD_ID = "apotheosis";

    private ApotheosisCompat() {
    }

    /**
     * An Apotheosis affix item that an augment put into a slot of a freshly spawned mob.
     */
    public record AugmentItem(EquipmentSlot slot, ResourceLocation rarity) {
    }

    /**
     * An elite forced by the spawnelite command instead of the augment items and the elite set roll.
     */
    public record ForcedElite(ResourceLocation rarity, boolean fullSet) {
    }

    /**
     * Result of making a renegade an elite: the upgraded pieces.
     */
    public record EliteResult(List<Upgraded> upgraded, boolean fullSet) {
        public static final EliteResult NONE = new EliteResult(List.of(), false);
    }

    public record Upgraded(EquipmentSlot slot, ResourceLocation rarity, ResourceLocation item, int affixes) {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Must run in finalizeSpawn before our pool equipment replaces the slots: the affix items that Apotheosis' augments
     * (FinalizeSpawnEvent, before finalizeSpawn) put on the mob.
     */
    public static List<AugmentItem> findAugmentItems(Mob mob) {
        return isLoaded() ? ApotheosisElites.findAugmentItems(mob) : List.of();
    }

    /**
     * Runs in finalizeSpawn after our pool equipment is on: upgrades the renegade's own pieces for the augment items
     * found before, or all of them for an elite set. Does nothing without Apotheosis or with apotheosisEliteGear off;
     * an elite forced by the spawnelite command only needs Apotheosis.
     */
    public static EliteResult applyEliteGear(RenegadeEntity renegade, ServerLevel level, MobSpawnType spawnType, List<AugmentItem> augmentItems,
                                             @Nullable ForcedElite forced) {
        if (!isLoaded() || (forced == null && !RenegadesConfig.APOTHEOSIS_ELITE_GEAR.get())) {
            return EliteResult.NONE;
        }
        return ApotheosisElites.applyEliteGear(renegade, level, spawnType, augmentItems, forced);
    }

    /**
     * Current color of a rarity, or empty if Apotheosis is missing or the rarity is unknown (e.g. after a datapack change).
     */
    public static OptionalInt rarityColor(ResourceLocation rarity) {
        return isLoaded() ? ApotheosisElites.rarityColor(rarity) : OptionalInt.empty();
    }

    /**
     * All rarity ids, most common first; empty without Apotheosis.
     */
    public static List<ResourceLocation> rarityIds() {
        return isLoaded() ? ApotheosisElites.rarityIds() : List.of();
    }
}
