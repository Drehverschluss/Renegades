package com.drehverschluss.renegades;

import com.drehverschluss.renegades.command.RenegadesCommand;
import com.drehverschluss.renegades.compat.dynamicdifficulty.DynamicDifficultyCompat;
import com.drehverschluss.renegades.compat.dynamicdifficulty.DynamicDifficultyLevelProvider;
import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.ArcherRenegade;
import com.drehverschluss.renegades.entity.BardRenegade;
import com.drehverschluss.renegades.entity.BerserkerRenegade;
import com.drehverschluss.renegades.entity.ElementalWizardRenegade;
import com.drehverschluss.renegades.entity.ForcemasterRenegade;
import com.drehverschluss.renegades.entity.PaladinRenegade;
import com.drehverschluss.renegades.entity.PriestRenegade;
import com.drehverschluss.renegades.entity.RenegadeClasses;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.drehverschluss.renegades.entity.RenegadeProjectiles;
import com.drehverschluss.renegades.entity.WizardRenegade;
import com.drehverschluss.renegades.magic.TickScheduler;
import com.drehverschluss.renegades.entity.RogueRenegade;
import com.drehverschluss.renegades.entity.WarriorRenegade;
import com.drehverschluss.renegades.equipment.EquipmentPoolLoader;
import com.drehverschluss.renegades.equipment.EquipmentPools;
import com.drehverschluss.renegades.equipment.RareItemLoader;
import com.drehverschluss.renegades.equipment.RareItems;
import com.drehverschluss.renegades.visual.CasterVisualsLoader;
import com.drehverschluss.renegades.group.RenegadeSpawner;
import com.drehverschluss.renegades.group.SpawnGroupLoader;
import com.drehverschluss.renegades.group.SpawnStats;
import com.drehverschluss.renegades.level.RenegadeLevels;
import com.drehverschluss.renegades.registry.ModEntityTypes;
import com.drehverschluss.renegades.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(Renegades.MODID)
public class Renegades {
    public static final String MODID = "renegades";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Renegades(IEventBus modEventBus, ModContainer modContainer) {
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, RenegadesConfig.SPEC);

        modEventBus.addListener(Renegades::registerAttributes);
        modEventBus.addListener(Renegades::registerSpawnPlacements);
        modEventBus.addListener(Renegades::addCreativeTabContents);

        NeoForge.EVENT_BUS.addListener(Renegades::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(Renegades::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(Renegades::registerCommands);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            SpawnStats.reset(event.getServer().getTickCount());
            warnIfFlightDisabled(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener(RenegadeSpawner::onServerTick);
        NeoForge.EVENT_BUS.addListener(RenegadeProjectiles::onProjectileImpact);
        TickScheduler.register();

        if (DynamicDifficultyCompat.isLoaded()) {
            RenegadeLevels.setProvider(new DynamicDifficultyLevelProvider());
            LOGGER.info("Dynamic Difficulty found; mob levels are available for equipment pool conditions");
        }
    }

    /**
     * Knockbacks, pulls and lifts (ours and those of the RPG mods' effects) can keep a player in the air for a moment;
     * with allow-flight=false the dedicated server may kick them for flying.
     */
    private static void warnIfFlightDisabled(net.minecraft.server.MinecraftServer server) {
        if (server.isDedicatedServer() && !server.isFlightAllowed()) {
            LOGGER.warn("allow-flight is false in server.properties: some renegade abilities (knockbacks, lifts, pulls) and effects of the RPG mods "
                    + "can keep players in the air for a moment, and the server may kick them with \"Flying is not enabled on this server\". "
                    + "Setting allow-flight=true is recommended.");
        }
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.WARRIOR.get(), WarriorRenegade.createAttributes().build());
        event.put(ModEntityTypes.ROGUE.get(), RogueRenegade.createAttributes().build());
        event.put(ModEntityTypes.PALADIN.get(), PaladinRenegade.createAttributes().build());
        event.put(ModEntityTypes.ARCHER.get(), ArcherRenegade.createAttributes().build());
        event.put(ModEntityTypes.WIZARD.get(), WizardRenegade.createAttributes().build());
        event.put(ModEntityTypes.PRIEST.get(), PriestRenegade.createAttributes().build());
        event.put(ModEntityTypes.ELEMENTAL_WIZARD.get(), ElementalWizardRenegade.createAttributes().build());
        event.put(ModEntityTypes.BERSERKER.get(), BerserkerRenegade.createAttributes().build());
        event.put(ModEntityTypes.BARD.get(), BardRenegade.createAttributes().build());
        event.put(ModEntityTypes.FORCEMASTER.get(), ForcemasterRenegade.createAttributes().build());
    }

    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        for (RenegadeClasses.RenegadeClass renegadeClass : RenegadeClasses.all()) {
            event.register(renegadeClass.entityType(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    RenegadeEntity::checkRenegadeSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            for (RenegadeClasses.RenegadeClass renegadeClass : RenegadeClasses.all()) {
                if (renegadeClass.isAvailable()) {
                    event.accept(renegadeClass.spawnEgg().get());
                }
            }
        }
    }

    private static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new EquipmentPoolLoader());
        event.addListener(new RareItemLoader());
        event.addListener(new CasterVisualsLoader());
        event.addListener(new SpawnGroupLoader());
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            EquipmentPools.validateTagsAndLog(event.getRegistryAccess());
            RareItems.validateTagsAndLog(event.getRegistryAccess());
        }
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        RenegadesCommand.register(event.getDispatcher());
    }
}
