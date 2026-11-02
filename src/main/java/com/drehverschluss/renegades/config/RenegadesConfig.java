package com.drehverschluss.renegades.config;

import java.util.Arrays;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config (config/renegades-server.toml; a copy in [world]/serverconfig/ overrides it for that world).
 * A server config because all of this is only evaluated on the logical server.
 */
public final class RenegadesConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue ARMOR_DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue WEAPON_DROP_CHANCE;
    public static final ModConfigSpec.BooleanValue REQUIRE_PLAYER_KILL;
    public static final ModConfigSpec.DoubleValue DROP_DURABILITY_MIN;
    public static final ModConfigSpec.DoubleValue DROP_DURABILITY_MAX;
    public static final ModConfigSpec.DoubleValue RARE_ITEM_CHANCE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEALTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.IntValue SPAWN_INTERVAL_SECONDS;
    public static final ModConfigSpec.DoubleValue SPAWN_CHANCE;
    public static final ModConfigSpec.IntValue MIN_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_DISTANCE;
    public static final ModConfigSpec.BooleanValue ALLOW_UNDERGROUND;
    public static final ModConfigSpec.BooleanValue REQUIRE_DARKNESS;
    public static final ModConfigSpec.IntValue MAX_NEARBY;
    public static final ModConfigSpec.IntValue CAP_RADIUS;
    public static final ModConfigSpec.BooleanValue PERSISTENT_NATURAL_SPAWNS;
    public static final ModConfigSpec.BooleanValue NO_SPAWN_IN_COLONIES;
    public static final ModConfigSpec.BooleanValue ENABLE_PARRY;
    public static final ModConfigSpec.BooleanValue ENABLE_DODGE;
    public static final ModConfigSpec.BooleanValue ENABLE_STEALTH;
    public static final ModConfigSpec.BooleanValue ENABLE_BRACE;
    public static final ModConfigSpec.BooleanValue ENABLE_LEAP;
    public static final ModConfigSpec.BooleanValue ENABLE_TRAPS;
    public static final ModConfigSpec.BooleanValue ENABLE_NOVA;
    public static final ModConfigSpec.BooleanValue ENABLE_PRIEST_HEALING;
    public static final ModConfigSpec.DoubleValue HEALING_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue COOLDOWN_GROUP_SCALING;
    public static final ModConfigSpec.BooleanValue ENABLE_BERSERKER_RAGE;
    public static final ModConfigSpec.BooleanValue ENABLE_GUILLOTINE;
    public static final ModConfigSpec.BooleanValue ENABLE_BARD_SONGS;
    public static final ModConfigSpec.BooleanValue ENABLE_ASAL;
    public static final ModConfigSpec.ConfigValue<String> CASTER_VISUALS;
    public static final ModConfigSpec.BooleanValue APOTHEOSIS_ELITE_GEAR;
    public static final ModConfigSpec.DoubleValue FULL_SET_ELITE_CHANCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Drop rules for the equipment worn by renegades. The regular loot table of each renegade is not affected.")
                .push("drops");

        ARMOR_DROP_CHANCE = builder
                .comment("Chance for each worn armor piece to drop.")
                .defineInRange("armorDropChance", 1.0D, 0.0D, 1.0D);
        WEAPON_DROP_CHANCE = builder
                .comment("Chance for each held item (main hand and off hand) to drop.")
                .defineInRange("weaponDropChance", 1.0D, 0.0D, 1.0D);
        REQUIRE_PLAYER_KILL = builder
                .comment("If true, equipment only drops when a player killed the renegade or hit it shortly before its death (like vanilla).")
                .define("requirePlayerKill", true);
        DROP_DURABILITY_MIN = builder
                .comment("Lowest remaining durability of a dropped damageable item, as a fraction of its max durability (rolled per item).",
                        "Items without durability or that are unbreakable are not changed; a dropped item never breaks. min = max = 1.0 drops everything undamaged.")
                .defineInRange("dropDurabilityMin", 0.25D, 0.0D, 1.0D);
        DROP_DURABILITY_MAX = builder
                .comment("Highest remaining durability of a dropped damageable item, as a fraction of its max durability.")
                .defineInRange("dropDurabilityMax", 0.75D, 0.0D, 1.0D);
        RARE_ITEM_CHANCE_MULTIPLIER = builder
                .comment("Multiplier for the chance of every rare item (data/<namespace>/renegades/rare_items, e.g. unique weapons that",
                        "replace a piece of the equipment on spawn). 0 = no rare items, 2 = twice as often.")
                .defineInRange("rareItemChanceMultiplier", 1.0D, 0.0D, 100.0D);

        builder.pop();

        builder.comment("Strength of all renegades. Multiplies the base value of the attributes when a renegade spawns,",
                        "before level scaling (e.g. Dynamic Difficulty) is applied on top. Existing renegades keep their values.")
                .push("strength");

        HEALTH_MULTIPLIER = builder
                .comment("Multiplier for the base max health.")
                .defineInRange("healthMultiplier", 1.0D, 0.1D, 10.0D);
        DAMAGE_MULTIPLIER = builder
                .comment("Multiplier for the base attack damage.")
                .defineInRange("damageMultiplier", 1.0D, 0.1D, 10.0D);

        builder.pop();

        builder.comment("Natural spawning of renegades. Renegades have their own spawn timer per player, independent of the vanilla",
                        "monster spawning and its mob cap. The biome tags #renegades:spawns/<class> (e.g. #renegades:spawns/warrior)",
                        "decide in which biomes a class may spawn; data/<namespace>/renegades/spawn_groups decides what spawns.")
                .push("spawns");

        SPAWN_INTERVAL_SECONDS = builder
                .comment("Every this many seconds, each player gets one spawn attempt.")
                .defineInRange("spawnIntervalSeconds", 60, 10, 3600);
        SPAWN_CHANCE = builder
                .comment("Chance that a spawn attempt goes ahead (e.g. 60 attempts per hour x 0.03 = about 1.8 spawns per hour and player).")
                .defineInRange("spawnChance", 0.03D, 0.0D, 1.0D);
        MIN_DISTANCE = builder
                .comment("Minimum horizontal distance in blocks between the player and the spawn position.")
                .defineInRange("minDistance", 24, 0, 256);
        MAX_DISTANCE = builder
                .comment("Maximum horizontal distance in blocks between the player and the spawn position.")
                .defineInRange("maxDistance", 64, 8, 256);
        ALLOW_UNDERGROUND = builder
                .comment("If true, half of the attempts look for a cave spot (within 32 blocks of the player's height) instead of the surface.")
                .define("allowUnderground", true);
        REQUIRE_DARKNESS = builder
                .comment("If true, renegades follow the normal monster light rules; if false, they also spawn in daylight.")
                .define("requireDarkness", false);
        MAX_NEARBY = builder
                .comment("No spawn (and no further group members) if this many renegades of any class are within capRadius of the spawn position or of the nearest player.")
                .defineInRange("maxNearby", 3, 1, 256);
        CAP_RADIUS = builder
                .comment("Radius in blocks for maxNearby.")
                .defineInRange("capRadius", 128, 8, 512);
        PERSISTENT_NATURAL_SPAWNS = builder
                .comment("If true, renegades from natural spawns, spawn eggs and commands never despawn. Renegades from (trial) spawners always despawn like monsters.")
                .define("persistentNaturalSpawns", true);
        NO_SPAWN_IN_COLONIES = builder
                .comment("If true and MineColonies is installed, renegades never spawn naturally inside a colony (leaders and group members).",
                        "Spawn eggs, spawners and commands are not affected. Without MineColonies this option does nothing.")
                .define("noSpawnInColonies", true);

        builder.pop();

        builder.comment("Combat behaviour of renegades. Timings, ranges and cooldowns are defined per class.")
                .push("combat");

        ENABLE_PARRY = builder
                .comment("If true, renegades that can parry (e.g. the warrior) sometimes parry close melee hits: half damage and a counter-strike.")
                .define("enableParry", true);

        ENABLE_DODGE = builder
                .comment("If true, renegades that can dodge (e.g. the rogue) sometimes evade a heavy hit (more than 4 damage) completely with a sidestep.")
                .define("enableDodge", true);

        ENABLE_STEALTH = builder
                .comment("If true, renegades that use stealth (e.g. the rogue) turn invisible while stalking their target. Armor and held items stay visible.")
                .define("enableStealth", true);

        ENABLE_BRACE = builder
                .comment("If true, renegades that brace (e.g. the paladin) sometimes stop to brace after a heavy hit: with a shield they block like a player, without one they take half damage.")
                .define("enableBrace", true);

        ENABLE_LEAP = builder
                .comment("If true, renegades that leap (e.g. the paladin) jump at distant targets and land with a ground impact.")
                .define("enableLeap", true);

        ENABLE_TRAPS = builder
                .comment("If true, renegades that set traps (e.g. the archer) throw traps that go off when an enemy comes close.")
                .define("enableTraps", true);

        ENABLE_NOVA = builder
                .comment("If true, renegades that cast novas (e.g. the wizard) blast an area around them when an enemy comes close.")
                .define("enableNova", true);

        ENABLE_PRIEST_HEALING = builder
                .comment("If true, renegades that heal (e.g. the priest) heal and shield their allies. If false, they only protect and attack.")
                .define("enablePriestHealing", true);

        HEALING_MULTIPLIER = builder
                .comment("Multiplier for all healing done by renegades. The caps per heal (e.g. at most 25 % of the target's max health) still apply.")
                .defineInRange("healingMultiplier", 1.0D, 0.0D, 5.0D);

        COOLDOWN_GROUP_SCALING = builder
                .comment("If true, renegades fighting near other renegades get somewhat shorter attack cooldowns: at most 25 % shorter than alone, never below 1 second.",
                        "If false, cooldowns are always as for a renegade fighting alone.")
                .define("cooldownGroupScaling", true);

        ENABLE_BERSERKER_RAGE = builder
                .comment("If true, the berserker gets stronger below 50 % health: up to +40 % damage, +25 % movement speed and shorter attack",
                        "cooldowns at 10 % health.")
                .define("enableBerserkerRage", true);

        ENABLE_GUILLOTINE = builder
                .comment("If true, the berserker leaps at targets 5-10 blocks away with an announced finishing blow (Guillotine).")
                .define("enableGuillotine", true);

        ENABLE_BARD_SONGS = builder
                .comment("If true, the bard plays songs for its allies (stacks of more damage or less damage taken); if false, it only uses its other abilities.")
                .define("enableBardSongs", true);

        ENABLE_ASAL = builder
                .comment("If true, the forcemaster unloads 5 arcane charges in an announced huge blow (Asal) and is exhausted for a moment afterwards.")
                .define("enableAsal", true);

        builder.pop();

        builder.comment("Look of the casters' spells (wizard, priest, ...).")
                .push("visuals");

        // Arrays.asList: List.of throws on contains(null) when an older config file lacks this key.
        CASTER_VISUALS = builder
                .comment("\"auto\": if Spell Engine is installed and the spell assigned in data/<namespace>/renegades/caster_visuals exists, casters' spells",
                        "use that spell's projectile model, particles and sounds; otherwise the built-in look. \"builtin\": always the built-in look.",
                        "Only the look changes; damage, targets, flight paths and cooldowns stay the same. Applies to new spells right away.")
                .defineInList("casterVisuals", "auto", Arrays.asList("auto", "builtin"));

        builder.pop();

        builder.comment("Elite renegades with Apotheosis (only if Apotheosis is installed). How often a renegade becomes an elite is decided",
                        "by Apotheosis' own augment data (data/apotheosis/apothic_augments, e.g. random_affix_items: 12 % of monster spawns).",
                        "Instead of a random Apotheosis item replacing a piece of the class gear, one of the renegade's own pieces gets the affixes",
                        "and rarity. Elites show the rarity as a title in their name, in the rarity's color.")
                .push("elite");

        APOTHEOSIS_ELITE_GEAR = builder
                .comment("If true, each Apotheosis affix item a renegade gets on spawn upgrades one of its own pieces instead (different pieces).",
                        "If false, Apotheosis has no effect on renegade gear. Also required for fullSetEliteChance.")
                .define("apotheosisEliteGear", true);
        FULL_SET_ELITE_CHANCE = builder
                .comment("Chance per spawn (not from (trial) spawners, only with a player nearby) that ALL worn pieces get affixes, all with the same",
                        "rarity rolled for the world tier (a small miniboss, shown as \"Elite\" in its name). 0 = never, e.g. 0.02 = 2 %.")
                .defineInRange("fullSetEliteChance", 0.0D, 0.0D, 1.0D);

        builder.pop();
        SPEC = builder.build();
    }

    private RenegadesConfig() {
    }
}
