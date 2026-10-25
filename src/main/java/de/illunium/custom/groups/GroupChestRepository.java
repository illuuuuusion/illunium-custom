package de.illunium.custom.groups;

import de.illunium.custom.core.command.UserException;
import de.illunium.custom.storage.Database;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

/** Only immutable snapshots cross the DB queue. Revisions reject stale writers. */
final class GroupChestRepository {
    record Snapshot(byte[] contents, int occupied, long revision) {
        Snapshot { contents = contents.clone(); }
        @Override public byte[] contents() { return contents.clone(); }
    }

    private final Database database;

    GroupChestRepository(Database database) { this.database = database; }

    CompletableFuture<Snapshot> load(long groupId, byte[] empty) {
        byte[] initial = empty.clone();
        return database.transaction(c -> {
            try (var s = c.prepareStatement("INSERT INTO group_chests(group_id, contents, saved_at) VALUES (?, ?, ?) ON CONFLICT(group_id) DO NOTHING")) {
                s.setLong(1, groupId); s.setBytes(2, initial); s.setLong(3, System.currentTimeMillis()); s.executeUpdate();
            }
            try (var s = c.prepareStatement("SELECT contents, occupied_slots, revision FROM group_chests WHERE group_id = ?")) {
                s.setLong(1, groupId);
                try (var r = s.executeQuery()) {
                    if (!r.next()) throw new SQLException("Missing group chest " + groupId);
                    return new Snapshot(r.getBytes(1), r.getInt(2), r.getLong(3));
                }
            }
        });
    }

    CompletableFuture<Void> save(long groupId, Snapshot snapshot) {
        return database.transaction(c -> {
            try (var s = c.prepareStatement("UPDATE group_chests SET contents = ?, occupied_slots = ?, revision = ?, saved_at = ? WHERE group_id = ? AND revision = ?")) {
                s.setBytes(1, snapshot.contents()); s.setInt(2, snapshot.occupied()); s.setLong(3, snapshot.revision());
                s.setLong(4, System.currentTimeMillis()); s.setLong(5, groupId); s.setLong(6, snapshot.revision() - 1);
                if (s.executeUpdate() != 1) throw new SQLException("Stale or missing group chest " + groupId);
            }
            return null;
        });
    }

    static void requireEmpty(Connection c, long groupId) throws SQLException {
        try (var s = c.prepareStatement("SELECT occupied_slots FROM group_chests WHERE group_id = ?")) {
            s.setLong(1, groupId);
            try (var r = s.executeQuery()) {
                if (r.next() && r.getInt(1) > 0) throw new UserException("Gruppenkiste zuerst vollständig leeren; die Gruppe wurde nicht aufgelöst.");
            }
        }
    }
}
