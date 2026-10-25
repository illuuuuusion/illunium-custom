CREATE TABLE group_chests (
    group_id INTEGER PRIMARY KEY REFERENCES groups(id) ON DELETE CASCADE,
    contents BLOB NOT NULL,
    occupied_slots INTEGER NOT NULL DEFAULT 0 CHECK (occupied_slots BETWEEN 0 AND 27),
    revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    saved_at INTEGER NOT NULL
);

CREATE TRIGGER protect_nonempty_group_chest
BEFORE DELETE ON groups
WHEN EXISTS (SELECT 1 FROM group_chests WHERE group_id = OLD.id AND occupied_slots > 0)
BEGIN
    SELECT RAISE(ABORT, 'group chest is not empty');
END;
