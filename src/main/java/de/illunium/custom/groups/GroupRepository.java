package de.illunium.custom.groups;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** SQL for {@code groups}/{@code group_memberships}. Runs inside {@code Database#transaction} only. */
final class GroupRepository {

    private static final String SELECT = """
            SELECT g.id, g.name, g.tag, g.color, g.leader_uuid, m.player_uuid, m.role
            FROM groups g JOIN group_memberships m ON m.group_id = g.id
            """;

    private GroupRepository() {
    }

    static Map<Long, Group> loadAll(Connection c) throws SQLException {
        return query(c, SELECT + "ORDER BY g.id, m.joined_at");
    }

    static Optional<Group> load(Connection c, long id) throws SQLException {
        return first(query(c, SELECT + "WHERE g.id = ? ORDER BY m.joined_at", id));
    }

    static Optional<Group> ofMember(Connection c, UUID uuid) throws SQLException {
        return first(query(c, SELECT + "WHERE g.id = (SELECT group_id FROM group_memberships WHERE player_uuid = ?)"
                + " ORDER BY m.joined_at", uuid.toString()));
    }

    /** Case-insensitive via the column's NOCASE collation; {@code column} is "name" or "tag". */
    static boolean taken(Connection c, String column, String value, long exceptId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT 1 FROM groups WHERE " + column + " = ? AND id <> ?")) {
            s.setString(1, value);
            s.setLong(2, exceptId);
            try (ResultSet r = s.executeQuery()) {
                return r.next();
            }
        }
    }

    /** Group plus the leader's membership row; the deferred FK checks both at commit. */
    static long insert(Connection c, String name, String tag, UUID leader, long now) throws SQLException {
        long id;
        try (PreparedStatement s = c.prepareStatement(
                "INSERT INTO groups (name, tag, leader_uuid, created_at) VALUES (?, ?, ?, ?) RETURNING id")) {
            s.setString(1, name);
            s.setString(2, tag);
            s.setString(3, leader.toString());
            s.setLong(4, now);
            try (ResultSet r = s.executeQuery()) {
                r.next();
                id = r.getLong(1);
            }
        }
        // The leader's own row role is ignored while leading; MOD is what they become after a transfer.
        addMember(c, id, leader, GroupRole.MOD, now);
        return id;
    }

    static void addMember(Connection c, long groupId, UUID uuid, GroupRole role, long now) throws SQLException {
        update(c, "INSERT INTO group_memberships (group_id, player_uuid, role, joined_at) VALUES (?, ?, ?, ?)",
                groupId, uuid.toString(), role.name(), now);
    }

    static void removeMember(Connection c, UUID uuid) throws SQLException {
        update(c, "DELETE FROM group_memberships WHERE player_uuid = ?", uuid.toString());
    }

    static void setRole(Connection c, UUID uuid, GroupRole role) throws SQLException {
        update(c, "UPDATE group_memberships SET role = ? WHERE player_uuid = ?", role.name(), uuid.toString());
    }

    /** {@code column} is "name", "tag" or "color". */
    static void set(Connection c, long id, String column, String value) throws SQLException {
        update(c, "UPDATE groups SET " + column + " = ? WHERE id = ?", value, id);
    }

    /** One commit: the old leader becomes MOD, the new one takes over. */
    static void setLeader(Connection c, long id, UUID oldLeader, UUID newLeader) throws SQLException {
        setRole(c, oldLeader, GroupRole.MOD);
        update(c, "UPDATE groups SET leader_uuid = ? WHERE id = ?", newLeader.toString(), id);
    }

    /** Memberships go with it (ON DELETE CASCADE). */
    static void delete(Connection c, long id) throws SQLException {
        update(c, "DELETE FROM groups WHERE id = ?", id);
    }

    private static Map<Long, Group> query(Connection c, String sql, Object... params) throws SQLException {
        record Row(String name, String tag, String color, UUID leader, Map<UUID, GroupRole> members) {
        }
        Map<Long, Row> rows = new LinkedHashMap<>();
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                s.setObject(i + 1, params[i]);
            }
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    Row row = rows.get(r.getLong(1));
                    if (row == null) {
                        row = new Row(r.getString(2), r.getString(3), r.getString(4),
                                UUID.fromString(r.getString(5)), new LinkedHashMap<>());
                        rows.put(r.getLong(1), row);
                    }
                    UUID member = UUID.fromString(r.getString(6));
                    row.members().put(member, member.equals(row.leader()) ? GroupRole.LEADER : GroupRole.valueOf(r.getString(7)));
                }
            }
        }
        Map<Long, Group> groups = new HashMap<>();
        rows.forEach((id, row) -> groups.put(id, new Group(id, row.name(), row.tag(),
                GroupColors.read(row.color()),
                row.leader(), Map.copyOf(row.members()))));
        return groups;
    }

    private static Optional<Group> first(Map<Long, Group> groups) {
        return groups.values().stream().findFirst();
    }

    private static void update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                s.setObject(i + 1, params[i]);
            }
            s.executeUpdate();
        }
    }
}
