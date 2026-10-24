package de.illunium.custom.whiteliststaging;

import static org.junit.jupiter.api.Assertions.*;
import de.illunium.custom.storage.Database;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WhitelistStageRepositoryTest {
    @TempDir Path dir;
    private final VerifiedProfile a = new VerifiedProfile(UUID.randomUUID(), "Alice"), b = new VerifiedProfile(UUID.randomUUID(), "Bob");
    @Test void stagingIsUuidBasedCaseInsensitivePersistentAndNeverGrantsNativeAccess() throws Exception {
        Path file = dir.resolve("data.db");
        try (Database db = Database.open(file)) {
            var r = new WhitelistStageRepository(db);
            for (int i = 0; i < 100; i++) r.stage(new VerifiedProfile(UUID.randomUUID(), "Player" + i), "CONSOLE", false).join();
            assertEquals("STAGED", r.stage(a, "CONSOLE", false).join());
            r.stage(new VerifiedProfile(a.uuid(), "ALICE"), "CONSOLE", false).join();
            r.stage(new VerifiedProfile(a.uuid(), "RenamedAlice"), "CONSOLE", false).join();
            assertThrows(CompletionException.class, () -> r.stage(new VerifiedProfile(UUID.randomUUID(), "renamedalice"), "OP", false).join());
            assertEquals(101, r.entries().join().size());
        }
        try (Database db = Database.open(file)) {
            var r = new WhitelistStageRepository(db);
            assertEquals(101, r.entries().join().size());
            assertTrue(r.entries().join().stream().allMatch(e -> e.status().equals("STAGED")));
            assertEquals(a.uuid(), r.unstage("renamedalice", "OP").join().uuid());
            assertEquals(100, r.entries().join().size());
        }
    }
    @Test void frozenLaunchPartialRetryCrashRecoveryAndManualRemovalDoNotReapplySuccesses() throws Exception {
        Path file = dir.resolve("recovery.db");
        try (Database db = Database.open(file)) {
            var r = new WhitelistStageRepository(db);
            r.stage(a, "OP", false).join();
            var frozen = r.claim("launch1", "OP", false).join();
            r.stage(b, "OP", false).join();
            assertEquals(List.of(a.uuid()), frozen.stream().map(e -> e.profile().uuid()).toList());
            assertThrows(CompletionException.class, () -> r.unstage(a.name(), "OP").join());
            assertEquals("APPLYING", r.entries().join().getFirst().status());
            // Crash after native write: the APPLYING intent is durable before acknowledgement.
        }
        try (Database db = Database.open(file)) {
            var r = new WhitelistStageRepository(db);
            var applying = r.entries().join().stream().filter(e -> e.status().equals("APPLYING")).findFirst().orElseThrow();
            r.result(applying, a, "APPLIED", null, "RECOVERY").join(); r.finishInterrupted().join();
            assertEquals("PARTIAL", r.latest().join().state());
            assertEquals("APPLIED", r.stage(a, "OP", false).join(), "manual native removal does not reactivate old APPLIED records");
            assertThrows(CompletionException.class, () -> r.unstage(a.name(), "OP").join());
            var next = r.claim("launch2", "OP", false).join(); assertEquals(List.of(b), next.stream().map(e -> e.profile()).toList());
            r.result(next.getFirst(), b, "FAILED", "Mojang unavailable", "OP").join(); r.finish("launch2").join();
            assertEquals(1, r.latest().join().failed()); assertEquals("PARTIAL", r.latest().join().state());
            var retry = r.claim("retry", "OP", true).join(); assertEquals(List.of(b), retry.stream().map(e -> e.profile()).toList());
            r.result(retry.getFirst(), b, "ALREADY_PRESENT", null, "OP").join(); r.finish("retry").join();
            assertEquals(1, r.latest().join().present()); assertEquals("DONE", r.latest().join().state());
            assertEquals(List.of(), r.claim("again", "OP", false).join());
            assertEquals(List.of(), r.claim("retry-again", "OP", true).join());
        }
    }
    @Test void alreadyNativeAccountIsNeverNewlyStaged() throws Exception {
        try (Database db = Database.open(dir.resolve("present.db"))) {
            var r = new WhitelistStageRepository(db);
            assertEquals("ALREADY_PRESENT", r.stage(a, "OP", true).join());
            r.stage(b, "OP", false).join();
            assertEquals("ALREADY_PRESENT", r.stage(b, "OP", true).join());
            assertEquals(List.of(), r.claim("launch", "OP", false).join());
        }
    }
    @Test void invalidAndFakeAccountsAreRejected() {
        for (String name : List.of("ab", "a".repeat(17), "Alice Bob", "@a", "Bob\nstop", "Bob;op")) assertThrows(RuntimeException.class, () -> VerifiedProfile.validateName(name));
        assertThrows(RuntimeException.class, () -> new VerifiedProfile(UUID.nameUUIDFromBytes("offline".getBytes()), "Alice"));
    }
}
