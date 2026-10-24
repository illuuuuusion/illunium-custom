package de.illunium.custom.playtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.illunium.custom.storage.Database;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaytimeTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @TempDir
    Path dir;

    private final AtomicLong nanos = new AtomicLong(123_456_789L);
    private final PlaytimeTracker tracker = new PlaytimeTracker(nanos::get);

    @Test
    void liveTimeGrowsAndRemaindersAreKept() {
        tracker.start(A);
        advance(59_500);
        assertEquals(59, tracker.unflushed(A));
        assertEquals(59, tracker.flush(A));
        assertEquals(0, tracker.unflushed(A));
        advance(600);
        assertEquals(1, tracker.unflushed(A), "the 0.5 s left over from the first flush must count");
        tracker.start(A);
        assertEquals(1, tracker.unflushed(A), "a second join event must not restart the session");
        assertEquals(0, tracker.unflushed(B));
    }

    @Test
    void checkpointAndQuitNeverCountTwice() throws Exception {
        try (Database db = Database.open(dir.resolve("data.db"))) {
            tracker.start(A);
            tracker.start(B);
            advance(90_000);
            write(db, tracker.flushAll());
            advance(30_000);
            write(db, Map.of(A, tracker.stop(A)));
            assertEquals(0, tracker.stop(A));
            advance(10_000);
            write(db, tracker.flushAll());

            assertEquals(120, total(db, A));
            assertEquals(130, total(db, B));
            assertEquals(0, total(db, UUID.randomUUID()));
        }
    }

    @Test
    void bulkLiveSnapshotUsesExactlyTheSameUnflushedSecondsAsCommands() throws Exception {
        try (Database db = Database.open(dir.resolve("bulk.db"))) {
            tracker.start(A); tracker.start(B); advance(70_500);
            write(db, tracker.flushAll()); advance(1_000);
            Map<UUID, Long> live = tracker.snapshot();
            assertEquals(Map.of(A, tracker.unflushed(A), B, tracker.unflushed(B)), live);
            assertEquals(71, total(db, A) + live.get(A));
            write(db, tracker.flushAll());
            assertEquals(71, total(db, A) + tracker.snapshot().get(A));
        }
    }

    @Test
    void storedTimeSurvivesRestart() throws Exception {
        Path file = dir.resolve("data.db");
        try (Database db = Database.open(file)) {
            write(db, Map.of(A, 3_600L));
        }
        try (Database db = Database.open(file)) {
            write(db, Map.of(A, 60L));
            assertEquals(3_660, total(db, A));
        }
    }

    @Test
    void formatsDaysHoursMinutes() {
        assertEquals("0m", PlaytimeTracker.format(59));
        assertEquals("1m", PlaytimeTracker.format(60));
        assertEquals("1h 0m", PlaytimeTracker.format(3_600));
        assertEquals("2d 0h 5m", PlaytimeTracker.format(2 * 86_400 + 5 * 60 + 59));
        assertEquals("1d 23h 59m", PlaytimeTracker.format(2 * 86_400 - 1));
    }

    private void advance(long millis) {
        nanos.addAndGet(millis * 1_000_000L);
    }

    private static void write(Database db, Map<UUID, Long> deltas) {
        db.transaction(c -> PlaytimeModule.add(c, deltas, 0)).join();
    }

    private static long total(Database db, UUID player) {
        return db.transaction(c -> PlaytimeModule.total(c, player)).join();
    }
}
