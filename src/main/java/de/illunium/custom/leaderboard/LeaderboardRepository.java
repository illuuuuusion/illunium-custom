package de.illunium.custom.leaderboard;

import de.illunium.custom.storage.Database;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class LeaderboardRepository {
    record Metrics(long placed, long totems, long spawn, long peak) {
        static final Metrics ZERO = new Metrics(0, 0, 0, 0);
        Metrics plus(Metrics other) {
            return new Metrics(placed + other.placed, totems + other.totems, spawn + other.spawn, Math.max(peak, other.peak));
        }
    }
    private final Database db;
    LeaderboardRepository(Database db) { this.db = db; }

    CompletableFuture<Map<UUID, Metrics>> load() {
        return db.transaction(c -> {
            Map<UUID, Metrics> result = new HashMap<>();
            try (PreparedStatement s = c.prepareStatement("SELECT player_uuid, placed_blocks, totems_used, spawn_successes, peak_xp_level FROM leaderboard_metrics");
                 ResultSet r = s.executeQuery()) {
                while (r.next()) result.put(UUID.fromString(r.getString(1)), new Metrics(r.getLong(2), r.getLong(3), r.getLong(4), r.getLong(5)));
            }
            return Map.copyOf(result);
        });
    }

    CompletableFuture<Void> save(Map<UUID, Metrics> deltas, Map<UUID, UUID> spawnEvents) {
        return db.transaction(c -> {
            Map<UUID, Metrics> values = new HashMap<>(deltas);
            long now = System.currentTimeMillis();
            try (PreparedStatement s = c.prepareStatement("INSERT OR IGNORE INTO leaderboard_spawn_events VALUES (?, ?, ?)")) {
                for (var event : spawnEvents.entrySet()) {
                    s.setString(1, event.getKey().toString()); s.setString(2, event.getValue().toString()); s.setLong(3, now);
                    if (s.executeUpdate() == 1) values.merge(event.getValue(), new Metrics(0, 0, 1, 0), Metrics::plus);
                }
            }
            try (PreparedStatement s = c.prepareStatement("""
                    INSERT INTO leaderboard_metrics VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                    placed_blocks = placed_blocks + excluded.placed_blocks,
                    totems_used = totems_used + excluded.totems_used,
                    spawn_successes = spawn_successes + excluded.spawn_successes,
                    peak_xp_level = MAX(peak_xp_level, excluded.peak_xp_level), updated_at = excluded.updated_at""")) {
                for (var entry : values.entrySet()) {
                    Metrics m = entry.getValue();
                    s.setString(1, entry.getKey().toString()); s.setLong(2, m.placed); s.setLong(3, m.totems);
                    s.setLong(4, m.spawn); s.setLong(5, m.peak); s.setLong(6, now); s.addBatch();
                }
                s.executeBatch();
            }
            return null;
        });
    }
}
