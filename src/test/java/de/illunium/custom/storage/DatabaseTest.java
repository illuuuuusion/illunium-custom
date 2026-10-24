package de.illunium.custom.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {

    private static final String A = "00000000-0000-0000-0000-00000000000a";
    private static final String B = "00000000-0000-0000-0000-00000000000b";
    private static final String C = "00000000-0000-0000-0000-00000000000c";

    @TempDir
    Path dir;

    @Test
    void migrationIsIdempotent() throws Exception {
        Path file = dir.resolve("data.db");
        try (Database db = Database.open(file)) {
            assertEquals(Database.MIGRATIONS.size(), db.schemaVersion());
        }
        try (Database db = Database.open(file)) {
            assertEquals(Database.MIGRATIONS.size(), db.schemaVersion());
            assertEquals((long) Database.MIGRATIONS.size(), count(db, "SELECT COUNT(*) FROM schema_migrations"));
            assertEquals(0L, count(db, "SELECT COUNT(*) FROM groups"));
        }
    }

    @Test
    void upgradeFromV2PreservesProfilesPlaytimeAndSpawnCooldowns() throws Exception {
        Path file = dir.resolve("upgrade.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file); Statement statement = c.createStatement()) {
            statement.executeUpdate("CREATE TABLE schema_migrations(version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)");
            for (int version = 1; version <= 2; version++) {
                try (var input = getClass().getResourceAsStream("/db/migrations/" + Database.MIGRATIONS.get(version - 1))) {
                    statement.executeUpdate(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
                statement.executeUpdate("INSERT INTO schema_migrations VALUES (" + version + ", 0)");
            }
            statement.executeUpdate("INSERT INTO player_profiles VALUES ('" + A + "', 'Steve', 0)");
            statement.executeUpdate("INSERT INTO playtime VALUES ('" + A + "', 12345, 0)");
            statement.executeUpdate("INSERT INTO spawn_cooldowns VALUES ('" + A + "', 9999999999999)");
        }
        try (Database db = Database.open(file)) {
            assertEquals(Database.MIGRATIONS.size(), db.schemaVersion());
            assertEquals(1L, count(db, "SELECT COUNT(*) FROM player_profiles"));
            assertEquals(12345L, count(db, "SELECT total_seconds FROM playtime"));
            assertEquals(9999999999999L, count(db, "SELECT available_at_epoch_ms FROM spawn_cooldowns"));
            assertEquals(0L, count(db, "SELECT COUNT(*) FROM leaderboard_metrics"));
            assertEquals(0L, count(db, "SELECT COUNT(*) FROM whitelist_stage_entries"));
        }
    }

    @Test
    void newerSchemaFailsWithoutTouchingData() throws Exception {
        Path file = dir.resolve("data.db");
        Database.open(file).close();
        exec(file, "INSERT INTO schema_migrations VALUES (99, 0)",
                "INSERT INTO player_profiles VALUES ('" + A + "', 'Steve', 0)");

        SQLException error = assertThrows(SQLException.class, () -> Database.open(file));
        assertTrue(error.getMessage().contains("newer"));
        assertEquals(1L, countRaw(file, "SELECT COUNT(*) FROM player_profiles"));
    }

    @Test
    void foreignSchemaFailsVisiblyAndKeepsExistingTable() throws Exception {
        Path file = dir.resolve("data.db");
        exec(file, "CREATE TABLE groups (whatever TEXT)", "INSERT INTO groups VALUES ('keep me')");

        assertThrows(SQLException.class, () -> Database.open(file));
        assertEquals(1L, countRaw(file, "SELECT COUNT(*) FROM groups WHERE whatever = 'keep me'"));
        assertEquals(0L, countRaw(file, "SELECT COUNT(*) FROM schema_migrations"));
    }

    @Test
    void corruptFileFails() throws Exception {
        Path file = dir.resolve("data.db");
        Files.writeString(file, "this is not a sqlite database, just garbage bytes ".repeat(100));
        assertThrows(SQLException.class, () -> Database.open(file));
    }

    @Test
    void readOnlyFileFails() throws Exception {
        Path file = dir.resolve("data.db");
        Database.open(file).close();
        assertTrue(file.toFile().setWritable(false));
        try {
            assertThrows(SQLException.class, () -> Database.open(file));
        } finally {
            file.toFile().setWritable(true);
        }
    }

    @Test
    void failedWorkRollsBack() throws Exception {
        try (Database db = Database.open(dir.resolve("data.db"))) {
            CompletableFuture<Void> failing = db.transaction(c -> {
                insertProfile(c, A);
                throw new SQLException("boom");
            });
            CompletionException error = assertThrows(CompletionException.class, failing::join);
            assertInstanceOf(Database.StorageException.class, error.getCause());
            assertEquals(0L, count(db, "SELECT COUNT(*) FROM player_profiles"));
        }
    }

    @Test
    void concurrentWritesAreSerializedAndCloseDrainsQueue() throws Exception {
        Path file = dir.resolve("data.db");
        Database db = Database.open(file);
        db.transaction(c -> update(c, "INSERT INTO playtime VALUES ('" + A + "', 0, 0)")).join();

        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 50; i++) {
                    db.transaction(c -> update(c, "UPDATE playtime SET total_seconds = total_seconds + 1"));
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        db.close();

        assertEquals(400L, countRaw(file, "SELECT total_seconds FROM playtime"));
        CompletionException error = assertThrows(CompletionException.class,
                () -> db.transaction(c -> 1).join());
        assertInstanceOf(Database.StorageException.class, error.getCause());
    }

    @Test
    void groupLeaderMustBeMemberOfSameGroup() throws Exception {
        try (Database db = Database.open(dir.resolve("data.db"))) {
            assertThrows(CompletionException.class, () -> db.transaction(c ->
                    update(c, "INSERT INTO groups (id, name, tag, leader_uuid, created_at) VALUES (1, 'Nova', 'NOVA', '" + A + "', 0)")
            ).join());

            db.transaction(c -> {
                update(c, "INSERT INTO groups (id, name, tag, leader_uuid, created_at) VALUES (1, 'Nova', 'NOVA', '" + A + "', 0)");
                return update(c, "INSERT INTO group_memberships VALUES (1, '" + A + "', 'MEMBER', 0)");
            }).join();

            // Leader removed without transfer -> rejected at commit, membership stays.
            assertThrows(CompletionException.class, () -> db.transaction(c ->
                    update(c, "DELETE FROM group_memberships WHERE player_uuid = '" + A + "'")).join());
            assertEquals(1L, count(db, "SELECT COUNT(*) FROM group_memberships"));

            // Disband removes group and memberships atomically.
            db.transaction(c -> update(c, "DELETE FROM groups WHERE id = 1")).join();
            assertEquals(0L, count(db, "SELECT COUNT(*) FROM group_memberships"));
        }
    }

    @Test
    void groupNameTagAndMembershipAreUnique() throws Exception {
        try (Database db = Database.open(dir.resolve("data.db"))) {
            db.transaction(c -> {
                update(c, "INSERT INTO groups (id, name, tag, leader_uuid, created_at) VALUES (1, 'Nova', 'NOVA', '" + A + "', 0)");
                return update(c, "INSERT INTO group_memberships VALUES (1, '" + A + "', 'MEMBER', 0)");
            }).join();

            assertThrows(CompletionException.class, () -> db.transaction(c -> {
                update(c, "INSERT INTO groups (id, name, tag, leader_uuid, created_at) VALUES (2, 'nova', 'X', '" + B + "', 0)");
                return update(c, "INSERT INTO group_memberships VALUES (2, '" + B + "', 'MEMBER', 0)");
            }).join());
            assertThrows(CompletionException.class, () -> db.transaction(c -> {
                update(c, "INSERT INTO groups (id, name, tag, leader_uuid, created_at) VALUES (2, 'Other', 'nova', '" + B + "', 0)");
                return update(c, "INSERT INTO group_memberships VALUES (2, '" + B + "', 'MEMBER', 0)");
            }).join());
            assertThrows(CompletionException.class, () -> db.transaction(c -> {
                update(c, "INSERT INTO groups (id, name, tag, leader_uuid, created_at) VALUES (2, 'Other', 'OTH', '" + A + "', 0)");
                return update(c, "INSERT INTO group_memberships VALUES (2, '" + A + "', 'MEMBER', 0)");
            }).join());
            assertEquals(1L, count(db, "SELECT COUNT(*) FROM groups"));
        }
    }

    @Test
    void marriagesAreCanonicalAndDisjoint() throws Exception {
        try (Database db = Database.open(dir.resolve("data.db"))) {
            db.transaction(c -> update(c, "INSERT INTO marriages (partner_a_uuid, partner_b_uuid, married_at) VALUES ('" + A + "', '" + B + "', 0)")).join();

            assertThrows(CompletionException.class, () -> db.transaction(c ->
                    update(c, "INSERT INTO marriages (partner_a_uuid, partner_b_uuid, married_at) VALUES ('" + B + "', '" + C + "', 0)")).join());
            assertThrows(CompletionException.class, () -> db.transaction(c ->
                    update(c, "INSERT INTO marriages (partner_a_uuid, partner_b_uuid, married_at) VALUES ('" + C + "', '" + A + "', 0)")).join());
            assertThrows(CompletionException.class, () -> db.transaction(c ->
                    update(c, "INSERT INTO marriages (partner_a_uuid, partner_b_uuid, married_at) VALUES ('" + C + "', '" + C + "', 0)")).join());
            assertEquals(1L, count(db, "SELECT COUNT(*) FROM marriages"));
        }
    }

    @Test
    void playtimeCannotBeNegative() throws Exception {
        try (Database db = Database.open(dir.resolve("data.db"))) {
            assertThrows(CompletionException.class, () -> db.transaction(c ->
                    update(c, "INSERT INTO playtime VALUES ('" + A + "', -1, 0)")).join());
        }
    }

    private static int update(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private static void insertProfile(Connection connection, String uuid) throws SQLException {
        update(connection, "INSERT INTO player_profiles VALUES ('" + uuid + "', 'Steve', 0)");
    }

    private static long count(Database db, String sql) {
        return db.transaction(c -> {
            try (Statement statement = c.createStatement(); ResultSet result = statement.executeQuery(sql)) {
                return result.getLong(1);
            }
        }).join();
    }

    private static long countRaw(Path file, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = c.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            return result.getLong(1);
        }
    }

    private static void exec(Path file, String... sql) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = c.createStatement()) {
            for (String s : sql) {
                statement.executeUpdate(s);
            }
        }
    }
}
