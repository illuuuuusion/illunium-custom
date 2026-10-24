package de.illunium.custom.whiteliststaging;

import de.illunium.custom.core.command.UserException;
import de.illunium.custom.storage.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class WhitelistStageRepository {
    record Entry(VerifiedProfile profile, String status, String actor, String launch, int attempts, String error) { }
    record Launch(String id, String state, int total, int applied, int present, int failed, int pending) { }
    private final Database db;
    WhitelistStageRepository(Database db) { this.db = db; }

    CompletableFuture<String> stage(VerifiedProfile profile, String actor, boolean present) {
        return db.transaction(c -> {
            try (PreparedStatement s = c.prepareStatement("""
                    INSERT INTO whitelist_stage_entries(player_uuid, verified_name, status, staged_by, staged_at)
                    VALUES (?, ?, ?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET verified_name=excluded.verified_name,
                    status=CASE WHEN excluded.status='ALREADY_PRESENT' THEN 'ALREADY_PRESENT' ELSE whitelist_stage_entries.status END
                    WHERE whitelist_stage_entries.status IN ('STAGED','FAILED')""")) {
                s.setString(1, profile.uuid().toString()); s.setString(2, profile.name());
                s.setString(3, present ? "ALREADY_PRESENT" : "STAGED"); s.setString(4, actor); s.setLong(5, System.currentTimeMillis());
                s.executeUpdate();
            }
            String status;
            try (PreparedStatement s = c.prepareStatement("SELECT status FROM whitelist_stage_entries WHERE player_uuid=?")) {
                s.setString(1, profile.uuid().toString()); try (ResultSet r = s.executeQuery()) { r.next(); status = r.getString(1); }
            }
            audit(c, actor, profile, "STAGE", status);
            return status;
        });
    }
    CompletableFuture<List<Entry>> entries() { return db.transaction(WhitelistStageRepository::entries); }
    private static List<Entry> entries(Connection c) throws SQLException {
        List<Entry> result = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT player_uuid, verified_name, status, staged_by, launch_id, attempt_count, last_error FROM whitelist_stage_entries ORDER BY verified_name COLLATE NOCASE, player_uuid"); ResultSet r = s.executeQuery()) {
            while (r.next()) result.add(new Entry(new VerifiedProfile(UUID.fromString(r.getString(1)), r.getString(2)), r.getString(3), r.getString(4), r.getString(5), r.getInt(6), r.getString(7)));
        }
        return List.copyOf(result);
    }
    CompletableFuture<VerifiedProfile> unstage(String name, String actor) {
        return db.transaction(c -> {
            Entry entry = entries(c).stream().filter(e -> e.profile.name().equalsIgnoreCase(name)).findFirst().orElseThrow(() -> new UserException("Kein vorgemerkter Account dieses Namens."));
            if (!List.of("STAGED", "FAILED").contains(entry.status)) throw new UserException("Eintrag bereits angewendet oder im Launch; native Freigaben nur über /whitelist remove entfernen.");
            try (PreparedStatement s = c.prepareStatement("DELETE FROM whitelist_stage_entries WHERE player_uuid=?")) {
                s.setString(1, entry.profile.uuid().toString()); s.executeUpdate();
            }
            audit(c, actor, entry.profile, "UNSTAGE", "REMOVED_STAGING_ONLY");
            return entry.profile;
        });
    }
    CompletableFuture<List<Entry>> claim(String id, String actor, boolean retry) {
        return db.transaction(c -> {
            List<Entry> selected = entries(c).stream().filter(e -> retry ? List.of("FAILED", "APPLYING").contains(e.status) : e.status.equals("STAGED")).toList();
            try (PreparedStatement s = c.prepareStatement("INSERT INTO whitelist_stage_launches VALUES (?, ?, ?, NULL, 'RUNNING')")) {
                s.setString(1, id); s.setString(2, actor); s.setLong(3, System.currentTimeMillis()); s.executeUpdate();
            }
            try (PreparedStatement s = c.prepareStatement("UPDATE whitelist_stage_entries SET status='APPLYING', launch_id=?, attempt_count=attempt_count+1, last_error=NULL WHERE player_uuid=?")) {
                for (Entry entry : selected) {
                    s.setString(1, id); s.setString(2, entry.profile.uuid().toString()); s.addBatch();
                    audit(c, actor, entry.profile, "CLAIM", id);
                }
                s.executeBatch();
            }
            return selected.stream().map(e -> new Entry(e.profile, "APPLYING", e.actor, id, e.attempts + 1, null)).toList();
        });
    }
    CompletableFuture<Void> result(Entry entry, VerifiedProfile profile, String status, String error, String actor) {
        return db.transaction(c -> {
            try (PreparedStatement s = c.prepareStatement("UPDATE whitelist_stage_entries SET verified_name=?, status=?, applied_at=?, last_error=? WHERE player_uuid=? AND status='APPLYING' AND launch_id=?")) {
                s.setString(1, profile.name()); s.setString(2, status);
                if (status.equals("FAILED")) s.setNull(3, java.sql.Types.BIGINT); else s.setLong(3, System.currentTimeMillis());
                s.setString(4, error); s.setString(5, profile.uuid().toString()); s.setString(6, entry.launch);
                if (s.executeUpdate() != 1) throw new SQLException("Staging claim missing for " + profile.uuid());
            }
            audit(c, actor, profile, "APPLY", status + (error == null ? "" : ": " + error));
            return null;
        });
    }
    CompletableFuture<Void> finish(String id) {
        return db.transaction(c -> {
            try (PreparedStatement s = c.prepareStatement("""
                    UPDATE whitelist_stage_launches SET finished_at=?, state=CASE
                    WHEN EXISTS(SELECT 1 FROM whitelist_stage_entries WHERE launch_id=? AND status IN ('FAILED','APPLYING'))
                    THEN 'PARTIAL' ELSE 'DONE' END WHERE launch_id=?""")) {
                s.setLong(1, System.currentTimeMillis()); s.setString(2, id); s.setString(3, id); s.executeUpdate();
            }
            return null;
        });
    }
    CompletableFuture<Void> finishInterrupted() {
        return db.transaction(c -> {
            try (var s = c.prepareStatement("UPDATE whitelist_stage_launches SET state='PARTIAL', finished_at=? WHERE state='RUNNING'")) {
                s.setLong(1, System.currentTimeMillis()); s.executeUpdate();
            }
            return null;
        });
    }
    CompletableFuture<Launch> latest() {
        return db.transaction(c -> {
            try (PreparedStatement s = c.prepareStatement("""
                    SELECT l.launch_id, l.state, COUNT(e.player_uuid),
                    COALESCE(SUM(e.status='APPLIED'),0), COALESCE(SUM(e.status='ALREADY_PRESENT'),0),
                    COALESCE(SUM(e.status='FAILED'),0), COALESCE(SUM(e.status='APPLYING'),0)
                    FROM whitelist_stage_launches l LEFT JOIN whitelist_stage_entries e ON e.launch_id=l.launch_id
                    GROUP BY l.launch_id ORDER BY l.started_at DESC, l.rowid DESC LIMIT 1"""); ResultSet r = s.executeQuery()) {
                return r.next() ? new Launch(r.getString(1), r.getString(2), r.getInt(3), r.getInt(4), r.getInt(5), r.getInt(6), r.getInt(7)) : null;
            }
        });
    }
    private static void audit(Connection c, String actor, VerifiedProfile p, String action, String result) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("INSERT INTO whitelist_stage_audit(actor, player_uuid, verified_name, action, result, recorded_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            s.setString(1, actor); s.setString(2, p.uuid().toString()); s.setString(3, p.name()); s.setString(4, action); s.setString(5, result); s.setLong(6, System.currentTimeMillis()); s.executeUpdate();
        }
    }
}
