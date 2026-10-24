package de.illunium.custom.leaderboard;

import static org.junit.jupiter.api.Assertions.*;
import de.illunium.custom.storage.Database;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LeaderboardRepositoryTest {
    @TempDir Path dir;
    @Test void additiveCountersMonotonicPeakAndSpawnDedupeSurviveRestart() throws Exception {
        UUID player = UUID.randomUUID(), event = UUID.randomUUID();
        Path file = dir.resolve("data.db");
        try (Database db = Database.open(file)) {
            var repository = new LeaderboardRepository(db);
            repository.save(Map.of(player, new LeaderboardRepository.Metrics(2, 1, 0, 40)), Map.of(event, player)).join();
            repository.save(Map.of(player, new LeaderboardRepository.Metrics(3, 0, 0, 2)), Map.of(event, player)).join();
            assertEquals(new LeaderboardRepository.Metrics(5, 1, 1, 40), repository.load().join().get(player));
        }
        try (Database db = Database.open(file)) {
            var repository = new LeaderboardRepository(db);
            repository.save(Map.of(), Map.of(event, player, UUID.randomUUID(), player)).join();
            assertEquals(new LeaderboardRepository.Metrics(5, 1, 2, 40), repository.load().join().get(player));
        }
    }
    @Test void trackerRetainsFailedBatchAndRetriesWithoutLosingNewDeltas() throws Exception {
        UUID player = UUID.randomUUID();
        try (Database db = Database.open(dir.resolve("retry.db"))) {
            db.transaction(c -> { try (var s = c.createStatement()) {
                s.executeUpdate("CREATE TRIGGER fail_metrics BEFORE INSERT ON leaderboard_metrics BEGIN SELECT RAISE(ABORT, 'injected'); END");
            } return null; }).join();
            var repository = new LeaderboardRepository(db);
            var tracker = new CustomMetricTracker(repository, Logger.getAnonymousLogger());
            tracker.add(player, new LeaderboardRepository.Metrics(2, 1, 0, 10));
            tracker.onSpawn(new de.illunium.custom.spawn.SpawnTeleportSucceeded(player, UUID.randomUUID()));
            db.transaction(c -> { try (var s = c.createStatement()) { s.executeUpdate("DROP TRIGGER fail_metrics"); } return null; }).join();
            tracker.add(player, new LeaderboardRepository.Metrics(3, 0, 0, 8)); tracker.close();
            assertEquals(new LeaderboardRepository.Metrics(5, 1, 1, 10), repository.load().join().get(player));
        }
    }
}
