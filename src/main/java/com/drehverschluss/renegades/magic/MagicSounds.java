package com.drehverschluss.renegades.magic;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Vanilla sounds for spells, by school (replaces ANARCHY's BossSounds, whose own sound files are not CC0).
 */
public final class MagicSounds {
    public static final SchoolSounds FIRE = new SchoolSounds(SoundEvents.FIRECHARGE_USE, SoundEvents.BLAZE_SHOOT, SoundEvents.FIRE_EXTINGUISH);
    public static final SchoolSounds FROST = new SchoolSounds(SoundEvents.POWDER_SNOW_STEP, SoundEvents.SNOW_GOLEM_SHOOT, SoundEvents.GLASS_BREAK);
    public static final SchoolSounds ARCANE = new SchoolSounds(SoundEvents.ENCHANTMENT_TABLE_USE, SoundEvents.EVOKER_CAST_SPELL, SoundEvents.AMETHYST_CLUSTER_BREAK);
    public static final SchoolSounds HEALING = new SchoolSounds(SoundEvents.AMETHYST_BLOCK_CHIME, SoundEvents.BEACON_POWER_SELECT, SoundEvents.AMETHYST_BLOCK_RESONATE);
    public static final SchoolSounds LIGHTNING = new SchoolSounds(SoundEvents.BEACON_AMBIENT, SoundEvents.TRIDENT_THUNDER.value(), SoundEvents.LIGHTNING_BOLT_IMPACT);
    public static final SchoolSounds SOUL = new SchoolSounds(SoundEvents.SOUL_ESCAPE.value(), SoundEvents.WITHER_SHOOT, SoundEvents.SCULK_CATALYST_BLOOM);
    public static final SchoolSounds WATER = new SchoolSounds(SoundEvents.BUCKET_FILL, SoundEvents.PLAYER_SPLASH, SoundEvents.GENERIC_SPLASH);
    public static final SchoolSounds EARTH = new SchoolSounds(SoundEvents.GRAVEL_HIT, SoundEvents.STONE_BREAK, SoundEvents.DRIPSTONE_BLOCK_BREAK);
    public static final SchoolSounds AIR = new SchoolSounds(SoundEvents.BREEZE_CHARGE, SoundEvents.BREEZE_SHOOT, SoundEvents.BREEZE_WIND_CHARGE_BURST.value());
    public static final SchoolSounds MELEE = new SchoolSounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.PLAYER_ATTACK_STRONG, SoundEvents.PLAYER_ATTACK_KNOCKBACK);
    public static final SchoolSounds RANGED = new SchoolSounds(SoundEvents.CROSSBOW_LOADING_START.value(), SoundEvents.ARROW_SHOOT, SoundEvents.ARROW_HIT);
    public static final SoundEvent EXPLOSION = SoundEvents.GENERIC_EXPLODE.value();
    private static final Map<MagicSchool, SchoolSounds> BY_SCHOOL = new EnumMap<>(MagicSchool.class);

    static {
        BY_SCHOOL.put(MagicSchool.GENERIC, ARCANE);
        BY_SCHOOL.put(MagicSchool.ARCANE, ARCANE);
        BY_SCHOOL.put(MagicSchool.FIRE, FIRE);
        BY_SCHOOL.put(MagicSchool.FROST, FROST);
        BY_SCHOOL.put(MagicSchool.HEALING, HEALING);
        BY_SCHOOL.put(MagicSchool.LIGHTNING, LIGHTNING);
        BY_SCHOOL.put(MagicSchool.SOUL, SOUL);
        BY_SCHOOL.put(MagicSchool.WATER, WATER);
        BY_SCHOOL.put(MagicSchool.EARTH, EARTH);
        BY_SCHOOL.put(MagicSchool.AIR, AIR);
        BY_SCHOOL.put(MagicSchool.PHYSICAL_MELEE, MELEE);
        BY_SCHOOL.put(MagicSchool.PHYSICAL_RANGED, RANGED);
    }

    private MagicSounds() {
    }

    public static SchoolSounds of(MagicSchool school) {
        return BY_SCHOOL.get(school);
    }

    public record SchoolSounds(SoundEvent windup, SoundEvent release, SoundEvent impact) {
    }
}
