// Based on MagicSchool from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: damage types in the renegades namespace; each school also knows its vanilla particles.
package com.drehverschluss.renegades.magic;

import com.drehverschluss.renegades.Renegades;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import org.joml.Vector3f;

/**
 * Schools of magic (the same as the Spell Power schools) plus the two physical kinds. Magical schools have their own
 * damage type (renegades:&lt;school&gt;), which counts as magic (c:is_magic) and bypasses armor and shields like
 * Spell Power's spell damage. Spell projectiles are the exception: a raised shield blocks them from the front, checked
 * and dealt with SPELL_PROJECTILE_DAMAGE (magic, but not in bypasses_armor, because vanilla's bypasses_shield contains
 * bypasses_armor; it is only used for the blocked hit, unblocked hits keep the school's damage type).
 */
public enum MagicSchool {
    GENERIC("generic", 0xFFFFFF, true),
    ARCANE("arcane", 0xFF66FF, true),
    FIRE("fire", 0xFF3300, true),
    FROST("frost", 0xCCFFFF, true),
    HEALING("healing", 0x66FF66, true),
    LIGHTNING("lightning", 0xFFFF66, true),
    SOUL("soul", 0x2DD4DA, true),
    PHYSICAL_MELEE("physical_melee", 0xB3B3B3, false),
    PHYSICAL_RANGED("physical_ranged", 0x805D4D, false);

    public static final ResourceKey<DamageType> SPELL_PROJECTILE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Renegades.MODID, "spell_projectile"));

    private final String name;
    private final int color;
    private final boolean magical;

    MagicSchool(String name, int color, boolean magical) {
        this.name = name;
        this.color = color;
        this.magical = magical;
    }

    public String schoolName() {
        return this.name;
    }

    public int color() {
        return this.color;
    }

    public boolean isMagical() {
        return this.magical;
    }

    public ResourceLocation spellPowerId() {
        return ResourceLocation.fromNamespaceAndPath("spell_power", this.name);
    }

    public ResourceKey<DamageType> damageType() {
        if (!this.magical) {
            throw new IllegalStateException(this + " is physical and has no magic damage type");
        }
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Renegades.MODID, this.name));
    }

    /**
     * Dust in the school's colour (trails, rings, telegraphs).
     */
    public ParticleOptions dust(float size) {
        return new DustParticleOptions(new Vector3f(((this.color >> 16) & 0xFF) / 255.0F, ((this.color >> 8) & 0xFF) / 255.0F, (this.color & 0xFF) / 255.0F), size);
    }

    /**
     * The school's signature particle (fire flames, snowflakes, ...).
     */
    public ParticleOptions particle() {
        return switch (this) {
            case FIRE -> ParticleTypes.FLAME;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case ARCANE -> ParticleTypes.WITCH;
            case HEALING -> ParticleTypes.HAPPY_VILLAGER;
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
            case SOUL -> ParticleTypes.SOUL_FIRE_FLAME;
            case GENERIC -> ParticleTypes.ENCHANT;
            case PHYSICAL_MELEE, PHYSICAL_RANGED -> ParticleTypes.CRIT;
        };
    }
}
