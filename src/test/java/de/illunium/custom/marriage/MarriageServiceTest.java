package de.illunium.custom.marriage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.illunium.custom.TestMainThread;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.storage.Database;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarriageServiceTest {

    // B sorts before A as a string, so canonical ordering is exercised.
    private static final UUID A = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @TempDir
    Path dir;

    private final TestMainThread main = new TestMainThread();
    /** Last reported partner per player; mirrors what the tablist receives. */
    private final Map<UUID, Optional<UUID>> shown = new HashMap<>();
    private Database db;
    private MarriageService marriages;

    @BeforeEach
    void open() throws Exception {
        db = Database.open(dir.resolve("data.db"));
        marriages = load();
    }

    @AfterEach
    void close() {
        db.close();
    }

    @Test
    void proposeAcceptAndSurviveRestart() {
        marriages.propose(A, B);
        assertEquals(A, main.await(marriages.accept(B)));
        assertEquals(Optional.of(B), marriages.partner(A));
        assertEquals(Optional.of(A), marriages.partner(B));
        assertEquals(Map.of(A, Optional.of(B), B, Optional.of(A)), shown);

        MarriageService restarted = load();
        assertEquals(Optional.of(B), restarted.partner(A));
        assertEquals(1L, count("SELECT COUNT(*) FROM marriages WHERE partner_a_uuid = '" + B + "'"));
    }

    @Test
    void proposalRules() {
        assertEquals("You can't marry yourself.", assertThrows(UserException.class, () -> marriages.propose(A, A)).getMessage());

        marriages.propose(A, B);
        assertEquals("You already proposed to that player.", assertThrows(UserException.class, () -> marriages.propose(A, B)).getMessage());
        assertEquals("That player already has a pending proposal.", assertThrows(UserException.class, () -> marriages.propose(C, B)).getMessage());
        assertEquals("That player already proposed to you. Use /marry accept.", assertThrows(UserException.class, () -> marriages.propose(B, A)).getMessage());

        assertEquals(A, marriages.deny(B));
        assertThrows(UserException.class, () -> marriages.accept(B));
        assertTrue(marriages.partner(B).isEmpty());

        marriages.propose(C, B);
        main.await(marriages.accept(B));
        assertEquals("That player is already married.", assertThrows(UserException.class, () -> marriages.propose(A, B)).getMessage());
        assertEquals("You are already married.", assertThrows(UserException.class, () -> marriages.propose(B, A)).getMessage());
    }

    @Test
    void proposalExpiresAfterFiveMinutes() {
        marriages.propose(A, B);
        main.advance(Duration.ofSeconds(299));
        marriages.propose(C, A);
        main.advance(Duration.ofSeconds(1));
        assertThrows(UserException.class, () -> marriages.accept(B));
        main.await(marriages.accept(A));
        assertEquals(Optional.of(C), marriages.partner(A));
    }

    @Test
    void concurrentAcceptsNeverMarryTwice() {
        // A proposed to B and C; both accept before either commit is visible on the main thread.
        marriages.propose(A, B);
        marriages.propose(A, C);
        CompletableFuture<UUID> first = marriages.accept(B);
        CompletableFuture<UUID> second = marriages.accept(C);
        main.await(first);
        assertEquals("One of you is already married.", main.fails(second));
        assertEquals(Optional.of(B), marriages.partner(A));
        assertTrue(marriages.partner(C).isEmpty());
        assertEquals(1L, count("SELECT COUNT(*) FROM marriages"));
    }

    @Test
    void divorceIsOneSidedAndNeedsConfirmationWithin30Seconds() {
        marriages.propose(A, B);
        main.await(marriages.accept(B));

        assertEquals(B, marriages.requestDivorce(A));
        main.advance(Duration.ofSeconds(30));
        assertThrows(UserException.class, () -> marriages.confirmDivorce(A));

        marriages.requestDivorce(A);
        marriages.cancelDivorce(A);
        assertThrows(UserException.class, () -> marriages.confirmDivorce(A));
        assertThrows(UserException.class, () -> marriages.cancelDivorce(A));

        marriages.requestDivorce(B);
        main.advance(Duration.ofSeconds(29));
        assertEquals(A, main.await(marriages.confirmDivorce(B)));
        assertTrue(marriages.partner(A).isEmpty());
        assertTrue(marriages.partner(B).isEmpty());
        assertEquals(Map.of(A, Optional.empty(), B, Optional.empty()), shown);
        assertEquals(0L, count("SELECT COUNT(*) FROM marriages"));
        assertEquals("You are not married.", assertThrows(UserException.class, () -> marriages.requestDivorce(A)).getMessage());
    }

    @Test
    void bothRequestingDivorceEndsItOnce() {
        marriages.propose(A, B);
        main.await(marriages.accept(B));
        marriages.requestDivorce(A);
        marriages.requestDivorce(B);
        main.await(marriages.confirmDivorce(A));
        assertThrows(UserException.class, () -> marriages.confirmDivorce(B));
    }

    private MarriageService load() {
        return MarriageService.load(db, main, main.clock(), (player, partner) -> shown.put(player, Optional.ofNullable(partner)));
    }

    private long count(String sql) {
        return db.transaction(c -> {
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
                return r.getLong(1);
            }
        }).join();
    }
}
