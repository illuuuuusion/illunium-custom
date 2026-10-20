-- Schema v1. UUIDs are canonical lowercase strings, timestamps are epoch milliseconds.

CREATE TABLE player_profiles (
    uuid TEXT PRIMARY KEY,
    last_known_name TEXT NOT NULL,
    last_seen_at INTEGER NOT NULL
);

-- Name and tag are ASCII-only (validated in the service), so NOCASE gives case-insensitive uniqueness.
-- The deferred composite FK guarantees that the leader is a member of this very group at every commit.
CREATE TABLE groups (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE COLLATE NOCASE,
    tag TEXT NOT NULL UNIQUE COLLATE NOCASE,
    color TEXT NOT NULL DEFAULT 'WHITE',
    leader_uuid TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (id, leader_uuid) REFERENCES group_memberships (group_id, player_uuid)
        DEFERRABLE INITIALLY DEFERRED
);

-- LEADER is not a role value; it is derived from groups.leader_uuid.
CREATE TABLE group_memberships (
    group_id INTEGER NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    player_uuid TEXT NOT NULL UNIQUE,
    role TEXT NOT NULL CHECK (role IN ('MEMBER', 'MOD')),
    joined_at INTEGER NOT NULL,
    PRIMARY KEY (group_id, player_uuid)
);

CREATE TABLE marriages (
    id INTEGER PRIMARY KEY,
    partner_a_uuid TEXT NOT NULL UNIQUE,
    partner_b_uuid TEXT NOT NULL UNIQUE,
    married_at INTEGER NOT NULL,
    CHECK (partner_a_uuid < partner_b_uuid)
);

-- UNIQUE covers each column; these triggers forbid a UUID appearing in both columns across rows.
CREATE TRIGGER marriages_disjoint_insert BEFORE INSERT ON marriages
WHEN EXISTS (
    SELECT 1 FROM marriages
    WHERE partner_a_uuid IN (NEW.partner_a_uuid, NEW.partner_b_uuid)
       OR partner_b_uuid IN (NEW.partner_a_uuid, NEW.partner_b_uuid)
)
BEGIN
    SELECT RAISE(ABORT, 'player is already married');
END;

CREATE TRIGGER marriages_disjoint_update BEFORE UPDATE OF partner_a_uuid, partner_b_uuid ON marriages
WHEN EXISTS (
    SELECT 1 FROM marriages
    WHERE id <> NEW.id
      AND (partner_a_uuid IN (NEW.partner_a_uuid, NEW.partner_b_uuid)
        OR partner_b_uuid IN (NEW.partner_a_uuid, NEW.partner_b_uuid))
)
BEGIN
    SELECT RAISE(ABORT, 'player is already married');
END;

CREATE TABLE playtime (
    player_uuid TEXT PRIMARY KEY,
    total_seconds INTEGER NOT NULL CHECK (total_seconds >= 0),
    updated_at INTEGER NOT NULL
);
