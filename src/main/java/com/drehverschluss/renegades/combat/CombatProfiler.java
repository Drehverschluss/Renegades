package com.drehverschluss.renegades.combat;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Debug timing of the renegade AI (/renegades profile start|stop). Costs nothing while stopped: callers only take the
 * time when {@link #enabled()} is true. Server thread only.
 */
public final class CombatProfiler {
    public enum Section {
        ENTITY_TICK("entity tick (vanilla AI, pathfinding, physics + brain)"),
        BRAIN("brain tick"),
        STIMULUS("stimulus (line of sight)"),
        DECIDE("decisions + movement"),
        ACTIONS("actions (swings, hits)"),
        HAZARD("hazard checks"),
        PATH("path checks (unreachable target)");

        private final String label;

        Section(String label) {
            this.label = label;
        }
    }

    private static boolean enabled;
    private static long startTick;
    private static final Map<Section, long[]> TOTALS = new EnumMap<>(Section.class);

    private CombatProfiler() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static long start() {
        return enabled ? System.nanoTime() : 0L;
    }

    public static void end(Section section, long startNanos) {
        if (enabled) {
            long[] total = TOTALS.computeIfAbsent(section, key -> new long[2]);
            total[0] += System.nanoTime() - startNanos;
            total[1]++;
        }
    }

    public static void begin(long serverTick) {
        TOTALS.clear();
        startTick = serverTick;
        enabled = true;
    }

    /**
     * Stops and returns the report: per section the total time per server tick and the average per call.
     */
    public static String finish(long serverTick) {
        enabled = false;
        long ticks = Math.max(1, serverTick - startTick);
        StringJoiner report = new StringJoiner("\n");
        report.add(String.format(Locale.ROOT, "Renegade AI over %d ticks (ms per server tick | calls per tick | µs per call):", ticks));
        for (Section section : Section.values()) {
            long[] total = TOTALS.get(section);
            if (total != null) {
                report.add(String.format(Locale.ROOT, "  %-55s %6.3f ms | %6.1f | %6.1f µs", section.label, total[0] / 1.0E6 / ticks,
                        total[1] / (double) ticks, total[0] / 1.0E3 / Math.max(1, total[1])));
            }
        }
        return report.toString();
    }
}
