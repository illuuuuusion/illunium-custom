CREATE TABLE whitelist_stage_launches (
    launch_id TEXT PRIMARY KEY,
    started_by TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    finished_at INTEGER,
    state TEXT NOT NULL CHECK (state IN ('RUNNING','DONE','PARTIAL','FAILED'))
);
CREATE TABLE whitelist_stage_entries (
    player_uuid TEXT PRIMARY KEY,
    verified_name TEXT NOT NULL COLLATE NOCASE UNIQUE,
    status TEXT NOT NULL CHECK (status IN ('STAGED','APPLYING','APPLIED','ALREADY_PRESENT','FAILED')),
    staged_by TEXT NOT NULL,
    staged_at INTEGER NOT NULL,
    applied_at INTEGER,
    launch_id TEXT REFERENCES whitelist_stage_launches(launch_id),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);
CREATE INDEX whitelist_stage_status ON whitelist_stage_entries(status);
CREATE TABLE whitelist_stage_audit (
    id INTEGER PRIMARY KEY,
    actor TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    verified_name TEXT NOT NULL,
    action TEXT NOT NULL,
    result TEXT NOT NULL,
    recorded_at INTEGER NOT NULL
);
