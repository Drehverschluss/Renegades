package com.drehverschluss.renegades.registry;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.entity.ArcherRenegade;
import com.drehverschluss.renegades.entity.ArcherTrap;
import com.drehverschluss.renegades.entity.ElementalWizardRenegade;
import com.drehverschluss.renegades.entity.PaladinRenegade;
import com.drehverschluss.renegades.entity.PriestRenegade;
import com.drehverschluss.renegades.entity.RogueRenegade;
import com.drehverschluss.renegades.entity.ThrowingKnife;
import com.drehverschluss.renegades.entity.WarriorRenegade;
import com.drehverschluss.renegades.entity.WizardRenegade;
import com.drehverschluss.renegades.magic.entity.MagicCloud;
import com.drehverschluss.renegades.magic.entity.MagicProjectile;
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

    public static final DeferredHolder<EntityType<?>, EntityType<RogueRenegade>> ROGUE = ENTITY_TYPES.register("rogue",
            () -> EntityType.Builder.of(RogueRenegade::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(8)
                    .build("rogue"));

    public static final DeferredHolder<EntityType<?>, EntityType<PaladinRenegade>> PALADIN = ENTITY_TYPES.register("paladin",
            () -> EntityType.Builder.of(PaladinRenegade::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(8)
                    .build("paladin"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArcherRenegade>> ARCHER = ENTITY_TYPES.register("archer",
            () -> EntityType.Builder.of(ArcherRenegade::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(8)
                    .build("archer"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArcherTrap>> ARCHER_TRAP = ENTITY_TYPES.register("archer_trap",
            () -> EntityType.Builder.<ArcherTrap>of(ArcherTrap::new, MobCategory.MISC)
                    .sized(0.5F, 0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .noSave()
                    .build("archer_trap"));

    public static final DeferredHolder<EntityType<?>, EntityType<WizardRenegade>> WIZARD = ENTITY_TYPES.register("wizard",
            () -> EntityType.Builder.of(WizardRenegade::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(8)
                    .build("wizard"));

    public static final DeferredHolder<EntityType<?>, EntityType<PriestRenegade>> PRIEST = ENTITY_TYPES.register("priest",
            () -> EntityType.Builder.of(PriestRenegade::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(8)
                    .build("priest"));

    // Always registered (same registry on server and client, old worlds keep loading); spawns only with its class mod,
    // see RenegadeClasses.
    public static final DeferredHolder<EntityType<?>, EntityType<ElementalWizardRenegade>> ELEMENTAL_WIZARD = ENTITY_TYPES.register("elemental_wizard",
            () -> EntityType.Builder.of(ElementalWizardRenegade::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(8)
                    .build("elemental_wizard"));

    public static final DeferredHolder<EntityType<?>, EntityType<MagicProjectile>> MAGIC_PROJECTILE = ENTITY_TYPES.register("magic_projectile",
            () -> EntityType.Builder.<MagicProjectile>of(MagicProjectile::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .build("magic_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<MagicCloud>> MAGIC_CLOUD = ENTITY_TYPES.register("magic_cloud",
            () -> EntityType.Builder.<MagicCloud>of(MagicCloud::new, MobCategory.MISC)
                    .sized(0.5F, 0.25F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .noSave()
                    .build("magic_cloud"));

    public static final DeferredHolder<EntityType<?>, EntityType<ThrowingKnife>> THROWING_KNIFE = ENTITY_TYPES.register("throwing_knife",
            () -> EntityType.Builder.<ThrowingKnife>of(ThrowingKnife::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("throwing_knife"));

    private ModEntityTypes() {
    }
}