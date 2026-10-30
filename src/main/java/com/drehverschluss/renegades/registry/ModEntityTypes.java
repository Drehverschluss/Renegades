package com.drehverschluss.renegades.registry;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.entity.WarriorRenegade;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Renegades.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<WarriorRenegade>> WARRIOR = ENTITY_TYPES.register("warrior",
            () -> EntityType.Builder.of(WarriorRenegade::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(8)
                    .build("warrior"));

    private ModEntityTypes() {
    }
}