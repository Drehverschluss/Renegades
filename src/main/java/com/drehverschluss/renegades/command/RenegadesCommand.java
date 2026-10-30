package com.drehverschluss.renegades.command;

import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.drehverschluss.renegades.equipment.EquipmentPoolEntry;
import com.drehverschluss.renegades.equipment.EquipmentPools;
import com.drehverschluss.renegades.equipment.EquipmentSelector;
import com.mojang.brigadier.CommandDispatcher;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Debug commands (permission level 2): /renegades pools [entity] and /renegades reequip [targets].
 */
public final class RenegadesCommand {
    private RenegadesCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("renegades")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("pools")
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        EquipmentPools.entityTypes().stream().map(BuiltInRegistries.ENTITY_TYPE::getKey), builder))
                                .executes(context -> listPools(context.getSource(), ResourceLocationArgument.getId(context, "entity")))))
                .then(Commands.literal("reequip")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> reequip(context.getSource(), EntityArgument.getEntities(context, "targets"))))));
    }

    private static int listPools(CommandSourceStack source, ResourceLocation entityId) {
        Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId);
        if (entityType.isEmpty()) {
            source.sendFailure(Component.literal("Unknown entity " + entityId));
            return 0;
        }

        EquipmentPools.Pool pool = EquipmentPools.get(entityType.get());
        source.sendSuccess(() -> Component.literal(entityId + ": " + pool.entries().size() + " entries (" + pool.fallbackCount()
                + " fallback) from " + pool.files().size() + " file(s)"), false);

        for (EquipmentPoolEntry entry : pool.entries()) {
            String slots = entry.equipment().entrySet().stream()
                    .map(slot -> slot.getKey().getName() + "=" + slot.getValue())
                    .collect(Collectors.joining(", "));
            source.sendSuccess(() -> Component.literal("- " + entry.id() + (entry.fallback() ? " [fallback]" : "")
                    + " | weight " + entry.weight() + " | " + entry.conditions().describe()
                    + " | " + slots + " | file " + entry.source()), false);
        }
        return pool.entries().size();
    }

    private static int reequip(CommandSourceStack source, Collection<? extends Entity> targets) {
        int count = 0;

        for (Entity target : targets) {
            if (!(target instanceof RenegadeEntity renegade) || !(target.level() instanceof ServerLevel level)) {
                continue;
            }
            count++;

            String name = target.getName().getString();
            Optional<EquipmentSelector.Selection> selection = EquipmentSelector.reequip(renegade, level);
            if (selection.isEmpty()) {
                String context = EquipmentSelector.currentContext(renegade, level).describe();
                source.sendSuccess(() -> Component.literal(name + ": no entry matches, equipment cleared | " + context), false);
                continue;
            }

            EquipmentSelector.Selection chosen = selection.get();
            source.sendSuccess(() -> Component.literal(name + ": '" + chosen.entry().id() + "'"
                    + (chosen.entry().fallback() ? " [fallback]" : "")
                    + " (1 of " + chosen.candidates() + " matching) from " + chosen.entry().source()
                    + " | " + chosen.context().describe()
                    + " | entry conditions: " + chosen.entry().conditions().describe()), false);
        }

        if (count == 0) {
            source.sendFailure(Component.literal("No renegades among the targets"));
        }
        return count;
    }
}
