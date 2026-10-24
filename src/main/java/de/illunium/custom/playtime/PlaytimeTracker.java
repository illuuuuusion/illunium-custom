package de.illunium.custom.playtime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Unflushed session time per online player, measured with a monotonic clock so wall-clock changes cannot produce
 * negative or doubled seconds. Each second is handed out by exactly one {@link #flush}/{@link #stop}. Main thread only.
 */
final class PlaytimeTracker {

    private static final long NANOS = 1_000_000_000L;

    private final LongSupplier nanoTime;
    private final Map<UUID, Long> unflushedSince = new HashMap<>();

    PlaytimeTracker(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    void start(UUID player) {
        unflushedSince.putIfAbsent(player, nanoTime.getAsLong());
    }

    /** Whole seconds not yet flushed; 0 for players without a session. */
    long unflushed(UUID player) {
        Long since = unflushedSince.get(player);
        return since == null ? 0 : (nanoTime.getAsLong() - since) / NANOS;
    }

    /** Takes the whole unflushed seconds; the sub-second remainder stays for the next flush. */
    long flush(UUID player) {
        Long since = unflushedSince.get(player);
        if (since == null) {
            return 0;
        }
        long seconds = (nanoTime.getAsLong() - since) / NANOS;
        unflushedSince.put(player, since + seconds * NANOS);
        return seconds;
    }

    /** Final flush and end of the session. */
    long stop(UUID player) {
        long seconds = flush(player);
        unflushedSince.remove(player);
        return seconds;
    }

    Map<UUID, Long> snapshot() {
        Map<UUID, Long> result = new HashMap<>();
        unflushedSince.keySet().forEach(id -> result.put(id, unflushed(id)));
        return Map.copyOf(result);
    }

    /** Non-zero flushes of all sessions. */
    Map<UUID, Long> flushAll() {
        Map<UUID, Long> deltas = new HashMap<>();
        for (UUID player : unflushedSince.keySet()) {
            long seconds = flush(player);
            if (seconds > 0) {
                deltas.put(player, seconds);
            }
        }
        return deltas;
    }

    static String format(long seconds) {
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        return (days > 0 ? days + "d " : "") + (days > 0 || hours > 0 ? hours + "h " : "") + minutes + "m";
    }
}
