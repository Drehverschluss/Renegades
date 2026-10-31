package com.drehverschluss.renegades.config;

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
    public static final ModConfigSpec.BooleanValue DROP_UNDAMAGED;
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
    public static final ModConfigSpec.BooleanValue ENABLE_PARRY;
    public static final ModConfigSpec.BooleanValue ENABLE_DODGE;
    public static final ModConfigSpec.BooleanValue ENABLE_STEALTH;
    public static final ModConfigSpec.BooleanValue ENABLE_BRACE;
    public static final ModConfigSpec.BooleanValue ENABLE_LEAP;
    public static final ModConfigSpec.BooleanValue ENABLE_TRAPS;

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
        DROP_UNDAMAGED = builder
                .comment("If true, dropped equipment keeps its durability; if false, it gets random damage like vanilla mob equipment.")
                .define("dropUndamaged", true);

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

        builder.pop();
        SPEC = builder.build();
    }

    private RenegadesConfig() {
    }
}
