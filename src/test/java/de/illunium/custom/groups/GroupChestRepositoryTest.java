package de.illunium.custom.groups;

import static org.junit.jupiter.api.Assertions.*;
import de.illunium.custom.TestMainThread;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.storage.Database;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroupChestRepositoryTest {
    @TempDir Path dir;

    @Test void savesAreAtomicOrderedAndSurviveRestartAndStaleWritersFail() throws Exception {
        Path file = dir.resolve("data.db");
        long id;
        byte[] empty = GroupChestCodec.encode(new ItemStack[27]);
        try (Database db = Database.open(file)) {
            var main = new TestMainThread();
            var groups = GroupService.load(db, main, main.clock(), (p,g) -> {});
            id = main.await(groups.create(UUID.randomUUID(), "Nova", "NOVA")).id();
            var repository = new GroupChestRepository(db);
            assertEquals(0, repository.load(id, empty).join().revision());
            var first = repository.save(id, new GroupChestRepository.Snapshot(new byte[]{1}, 2, 1));
            var second = repository.save(id, new GroupChestRepository.Snapshot(new byte[]{2}, 3, 2));
            first.join(); second.join();
            assertThrows(CompletionException.class, () -> repository.save(id, new GroupChestRepository.Snapshot(empty, 0, 1)).join());
            var snapshot = repository.load(id, empty).join();
            assertArrayEquals(new byte[]{2}, snapshot.contents()); assertEquals(3, snapshot.occupied()); assertEquals(2, snapshot.revision());
            assertThrows(IllegalArgumentException.class, () -> GroupChestCodec.decode(snapshot.contents()));
            assertArrayEquals(new byte[]{2}, repository.load(id, empty).join().contents(), "bad data must not be overwritten");
        }
        try (Database db = Database.open(file)) {
            var saved = new GroupChestRepository(db).load(id, empty).join();
            assertArrayEquals(new byte[]{2}, saved.contents()); assertEquals(3, saved.occupied()); assertEquals(2, saved.revision());
        }
    }

    @Test void normalAndAdminDisbandAndRawSqlCannotDeleteItemsButEmptyChestCascades() throws Exception {
        try (Database db = Database.open(dir.resolve("data.db"))) {
            var main = new TestMainThread(); var leader = UUID.randomUUID();
            var groups = GroupService.load(db, main, main.clock(), (p,g) -> {});
            long id = main.await(groups.create(leader, "Nova", "NOVA")).id();
            var repository = new GroupChestRepository(db);
            byte[] empty = GroupChestCodec.encode(new ItemStack[27]);
            repository.load(id, empty).join();
            repository.save(id, new GroupChestRepository.Snapshot(new byte[]{1}, 1, 1)).join();
            main.await(groups.requestDisband(leader));
            assertTrue(main.fails(groups.confirm(leader)).contains("leeren"));
            assertTrue(main.fails(groups.adminDisband(id)).contains("leeren"));
            assertThrows(CompletionException.class, () -> db.transaction(c -> {
                try (var s = c.createStatement()) { s.executeUpdate("DELETE FROM groups"); } return null;
            }).join());
            repository.save(id, new GroupChestRepository.Snapshot(empty, 0, 2)).join();
            main.await(groups.adminDisband(id));
            assertEquals(0, db.transaction(c -> {
                try (var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM group_chests")) { return r.getInt(1); }
            }).join());
            long newId = main.await(groups.create(leader, "New", "NEW")).id();
            assertArrayEquals(empty, repository.load(newId, empty).join().contents());
        }
    }

    @Test void guardProtectsUncommittedSessionsAndPendingDeleteReservesIdOnMainThread() throws Exception {
        try (Database db = Database.open(dir.resolve("data.db"))) {
            var main = new TestMainThread(); var groups = GroupService.load(db, main, main.clock(), (p,g) -> {});
            long id = main.await(groups.create(UUID.randomUUID(), "Nova", "NOVA")).id();
            groups.guardDisband(groupId -> { throw new UserException("Close and flush first"); });
            assertThrows(UserException.class, () -> groups.adminDisband(id));
            groups.guardDisband(groupId -> {});
            var deleting = groups.adminDisband(id);
            assertTrue(groups.disbanding(id));
            main.await(deleting);
            assertFalse(groups.disbanding(id));
        }
    }
}
