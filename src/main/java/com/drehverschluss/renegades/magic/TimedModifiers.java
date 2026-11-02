package com.drehverschluss.renegades.magic;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Attribute modifiers that are removed again after some ticks (transient, so they are never saved). Applying the same
 * modifier again refreshes its duration.
 */
public final class TimedModifiers {
    private static final Map<LivingEntity, Map<ResourceLocation, Long>> EXPIRY = new WeakHashMap<>();

    private TimedModifiers() {
    }

    /**
     * Adds the modifier to the attribute with this registry id (e.g. another mod's attribute) for the given ticks;
     * does nothing if the attribute is not registered or the entity does not have it.
     */
    public static boolean apply(ServerLevel level, LivingEntity entity, ResourceLocation attributeId, ResourceLocation modifierId, double amount,
                                AttributeModifier.Operation operation, int ticks) {
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).<Holder<Attribute>>map(holder -> holder).orElse(null);
        AttributeInstance instance = attribute == null ? null : entity.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        instance.removeModifier(modifierId);
        instance.addTransientModifier(new AttributeModifier(modifierId, amount, operation));
        long until = level.getGameTime() + ticks;
        EXPIRY.computeIfAbsent(entity, key -> new java.util.HashMap<>()).put(modifierId, until);
        TickScheduler.schedule(level, ticks, () -> {
            Map<ResourceLocation, Long> modifiers = EXPIRY.get(entity);
            Long expiry = modifiers == null ? null : modifiers.get(modifierId);
            // A later application refreshed it; that one removes it.
            if (expiry != null && expiry <= level.getGameTime()) {
                modifiers.remove(modifierId);
                instance.removeModifier(modifierId);
            }
        });
        return true;
    }
}
