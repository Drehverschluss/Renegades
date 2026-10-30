package com.drehverschluss.renegades.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config (config/renegades-server.toml; a copy in [world]/serverconfig/ overrides it for that world).
 * A server config because drop rules are only evaluated on the logical server.
 */
public final class RenegadesConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue ARMOR_DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue WEAPON_DROP_CHANCE;
    public static final ModConfigSpec.BooleanValue REQUIRE_PLAYER_KILL;
    public static final ModConfigSpec.BooleanValue DROP_UNDAMAGED;

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
        SPEC = builder.build();
    }

    private RenegadesConfig() {
    }
}
