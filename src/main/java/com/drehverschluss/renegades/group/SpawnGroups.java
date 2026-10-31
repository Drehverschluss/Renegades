package com.drehverschluss.renegades.group;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.equipment.SpawnContext;
import com.drehverschluss.renegades.level.RenegadeLevels;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.biome.Biome;

/**
 * Loaded spawn group templates. Replaced as a whole on every data reload.
 */
public final class SpawnGroups {
    private static volatile Map<ResourceLocation, SpawnGroupTemplate> templates = Map.of();

    private SpawnGroups() {
    }

    static void set(Map<ResourceLocation, SpawnGroupTemplate> loaded) {
        templates = Map.copyOf(loaded);
    }

    public static Collection<SpawnGroupTemplate> all() {
        return templates.values();
    }

    /**
     * Finds a template by full id, or by path alone if it is unique (so "warrior_pair" finds renegades:warrior_pair).
     */
    public static Optional<SpawnGroupTemplate> find(ResourceLocation id) {
        SpawnGroupTemplate exact = templates.get(id);
        if (exact != null) {
            return Optional.of(exact);
        }
        List<SpawnGroupTemplate> byPath = templates.values().stream().filter(template -> template.id().getPath().equals(id.getPath())).toList();
        return byPath.size() == 1 ? Optional.of(byPath.getFirst()) : Optional.empty();
    }

    /**
     * Weighted pick among the templates for this leader whose conditions match; empty means the leader stays alone.
     */
    @Nullable
    public static SpawnGroupTemplate pick(Mob leader, ServerLevel level) {
        OptionalInt mobLevel = RenegadeLevels.provider().map(provider -> provider.currentLevel(leader)).orElse(OptionalInt.empty());
        SpawnContext context = SpawnContext.of(leader, level, mobLevel);
        return pickWeighted(templates.values().stream()
                .filter(template -> template.leader() == leader.getType() && template.conditions().test(context))
                .toList(), leader.getRandom());
    }

    /**
     * Spawn timer: weighted pick among all templates whose leader may spawn in the biome at this position (biome tag
     * #renegades:spawns/[leader]) and whose conditions match. The mob level is not known yet, so level bounds count as
     * met here; the spawned leader rolls its final template in finalizeSpawn with its real level.
     */
    @Nullable
    public static SpawnGroupTemplate pickForPosition(ServerLevel level, BlockPos pos, RandomSource random) {
        Holder<Biome> biome = level.getBiome(pos);
        SpawnContext context = SpawnContext.at(level, pos, OptionalInt.empty());
        return pickWeighted(templates.values().stream()
                .filter(template -> biome.is(spawnBiomeTag(template.leader())) && template.conditions().test(context))
                .toList(), random);
    }

    /**
     * Biome tag that allows a class to spawn: #renegades:spawns/warrior for renegades:warrior,
     * #renegades:spawns/[namespace]/[path] for entities of other mods.
     */
    public static TagKey<Biome> spawnBiomeTag(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        String path = Renegades.MODID.equals(id.getNamespace()) ? id.getPath() : id.getNamespace() + "/" + id.getPath();
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Renegades.MODID, "spawns/" + path));
    }

    @Nullable
    private static SpawnGroupTemplate pickWeighted(List<SpawnGroupTemplate> matching, RandomSource random) {
        if (matching.isEmpty()) {
            return null;
        }

        int roll = random.nextInt(matching.stream().mapToInt(SpawnGroupTemplate::weight).sum());
        for (SpawnGroupTemplate template : matching) {
            roll -= template.weight();
            if (roll < 0) {
                return template;
            }
        }
        throw new IllegalStateException("Weighted pick out of range");
    }
}
