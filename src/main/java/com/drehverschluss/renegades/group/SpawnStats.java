package com.drehverschluss.renegades.group;

/**
 * Counters of the renegade spawn timer since server start (or the last reset), for /renegades stats.
 * Only touched on the server thread.
 */
public final class SpawnStats {
    private static long startTick;
    private static long attempts;
    private static long failedChance;
    private static long noPosition;
    private static long noBiome;
    private static long capped;
    private static long leaders;
    private static long members;

    private SpawnStats() {
    }

    public enum Result {
        FAILED_CHANCE, NO_POSITION, NO_BIOME, CAPPED, SPAWNED
    }

    public static void reset(long currentTick) {
        startTick = currentTick;
        attempts = 0;
        failedChance = 0;
        noPosition = 0;
        noBiome = 0;
        capped = 0;
        leaders = 0;
        members = 0;
    }

    public static void recordAttempt(Result result) {
        attempts++;
        switch (result) {
            case FAILED_CHANCE -> failedChance++;
            case NO_POSITION -> noPosition++;
            case NO_BIOME -> noBiome++;
            case CAPPED -> capped++;
            case SPAWNED -> leaders++;
        }
    }

    public static void recordMembers(int count) {
        members += count;
    }

    public record Snapshot(long ticks, long attempts, long failedChance, long noPosition, long noBiome, long capped, long leaders, long members) {
        public double perHour(long value) {
            // 72000 ticks = one hour at 20 TPS
            return this.ticks <= 0 ? 0.0D : value * 72000.0D / this.ticks;
        }
    }

    public static Snapshot snapshot(long currentTick) {
        return new Snapshot(currentTick - startTick, attempts, failedChance, noPosition, noBiome, capped, leaders, members);
    }
}
