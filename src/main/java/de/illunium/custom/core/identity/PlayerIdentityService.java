package de.illunium.custom.core.identity;

import de.illunium.custom.storage.Database;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** UUID is the identity; the name is only the last one seen on join. */
public final class PlayerIdentityService implements Listener {

    private final Database database;
    private final Logger logger;
    // ponytail: every known profile in memory; fine for one community server, query the DB if it grows to 100k+.
    private final Map<UUID, String> names;

    private PlayerIdentityService(Database database, Logger logger, Map<UUID, String> names) {
        this.database = database;
        this.logger = logger;
        this.names = new ConcurrentHashMap<>(names);
    }

    /** Loads all stored profiles; blocks, so call only during startup. */
    public static PlayerIdentityService load(Database database, Logger logger) {
        Map<UUID, String> names = database.transaction(connection -> {
            Map<UUID, String> result = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid, last_known_name FROM player_profiles");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.put(UUID.fromString(rows.getString(1)), rows.getString(2));
                }
            }
            return result;
        }).join();
        return new PlayerIdentityService(database, logger, names);
    }

    public Map<UUID, String> knownPlayers() { return Map.copyOf(names); }

    public Optional<String> name(UUID uuid) {
        return Optional.ofNullable(names.get(uuid));
    }

    /** Case-insensitive; several UUIDs can share a last known name after renames. */
    public List<UUID> findByName(String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        return names.entrySet().stream()
                .filter(entry -> entry.getValue().toLowerCase(Locale.ROOT).equals(wanted))
                .map(Map.Entry::getKey)
                .toList();
    }

    public void remember(UUID uuid, String name) {
        names.put(uuid, name);
        long now = System.currentTimeMillis();
        database.transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_profiles (uuid, last_known_name, last_seen_at) VALUES (?, ?, ?)
                    ON CONFLICT (uuid) DO UPDATE SET
                        last_known_name = excluded.last_known_name,
                        last_seen_at = excluded.last_seen_at""")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setLong(3, now);
                return statement.executeUpdate();
            }
        }).exceptionally(error -> {
            logger.log(Level.SEVERE, "Could not store profile " + uuid + " (" + name + ")", error);
            return null;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        remember(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
