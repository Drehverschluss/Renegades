// Based on TickScheduler from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: NeoForge events instead of Architectury.
package com.drehverschluss.renegades.magic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Runs actions a few ticks later on the server (staggered projectiles of a volley). Pending actions are dropped when
 * the server stops.
 */
public final class TickScheduler {
    private static final Map<ResourceKey<Level>, List<Task>> PENDING = new HashMap<>();

    private TickScheduler() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                drain(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> PENDING.clear());
    }

    public static void schedule(ServerLevel level, int delay, Runnable action) {
        PENDING.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(new Task(level.getGameTime() + Math.max(0, delay), action));
    }

    private static void drain(ServerLevel level) {
        List<Task> tasks = PENDING.get(level.dimension());
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        List<Task> due = new ArrayList<>();
        tasks.removeIf(task -> {
            if (task.dueTick() <= now) {
                due.add(task);
                return true;
            }
            return false;
        });
        for (Task task : due) {
            task.action().run();
        }
    }

    private record Task(long dueTick, Runnable action) {
    }
}
