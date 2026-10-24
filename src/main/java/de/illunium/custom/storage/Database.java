package de.illunium.custom.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Single SQLite connection owned by one dedicated thread. Every unit of work runs as its own
 * transaction, in submission order. Never call Bukkit API from inside a {@link SqlWork}.
 */
public final class Database implements AutoCloseable {

    /** Migration scripts in order; version = index + 1. Append only, never edit a shipped script. */
    static final List<String> MIGRATIONS = List.of("V1__initial_schema.sql", "V2__spawn_cooldowns.sql",
            "V3__leaderboards.sql", "V4__whitelist_staging.sql");

    private final Connection connection;
    private final ExecutorService executor;
    private final int schemaVersion;

    private Database(Connection connection, int schemaVersion) {
        this.connection = connection;
        this.schemaVersion = schemaVersion;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "IlluniumCustom-DB");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Opens the database and applies pending migrations on the calling thread (startup only). */
    public static Database open(Path file) throws SQLException, IOException {
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        if (!Files.isWritable(directory) || (Files.exists(file) && !Files.isWritable(file))) {
            throw new SQLException("Database is not writable: " + file.toAbsolutePath());
        }

        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("PRAGMA journal_mode = WAL");
            }
            return new Database(connection, migrate(connection));
        } catch (SQLException | IOException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }

    static int migrate(Connection connection) throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        version INTEGER PRIMARY KEY,
                        applied_at INTEGER NOT NULL
                    )""");
        }

        int current;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
            current = result.getInt(1);
        }
        if (current > MIGRATIONS.size()) {
            throw new SQLException("Database schema v" + current + " is newer than supported v"
                    + MIGRATIONS.size() + "; restore a matching plugin version or database backup");
        }

        for (int version = current + 1; version <= MIGRATIONS.size(); version++) {
            String script = readScript(MIGRATIONS.get(version - 1));
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(script);
                statement.executeUpdate("INSERT INTO schema_migrations (version, applied_at) VALUES ("
                        + version + ", " + System.currentTimeMillis() + ")");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new SQLException("Migration v" + version + " failed: " + e.getMessage(), e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return MIGRATIONS.size();
    }

    private static String readScript(String name) throws IOException {
        try (InputStream input = Database.class.getResourceAsStream("/db/migrations/" + name)) {
            if (input == null) {
                throw new IOException("Missing migration resource " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    /** Runs {@code work} in one transaction on the DB thread; rolled back if it throws. */
    public <T> CompletableFuture<T> transaction(SqlWork<T> work) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    connection.setAutoCommit(false);
                    try {
                        T result = work.run(connection);
                        connection.commit();
                        return result;
                    } catch (SQLException | RuntimeException e) {
                        connection.rollback();
                        throw e;
                    } finally {
                        connection.setAutoCommit(true);
                    }
                } catch (SQLException e) {
                    throw new StorageException(e);
                }
            }, executor);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(new StorageException("Database is closed", e));
        }
    }

    /** Rejects new work, finishes everything already queued, then closes the connection. */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new StorageException("Timed out waiting for pending database work");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageException("Interrupted while closing database", e);
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new StorageException(e);
            }
        }
    }

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    public static final class StorageException extends RuntimeException {
        StorageException(Throwable cause) {
            super(cause);
        }

        StorageException(String message) {
            super(message);
        }

        StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
