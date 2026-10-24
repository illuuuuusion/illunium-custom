CREATE TABLE leaderboard_metrics (
    player_uuid TEXT PRIMARY KEY,
    placed_blocks INTEGER NOT NULL DEFAULT 0 CHECK (placed_blocks >= 0),
    totems_used INTEGER NOT NULL DEFAULT 0 CHECK (totems_used >= 0),
    spawn_successes INTEGER NOT NULL DEFAULT 0 CHECK (spawn_successes >= 0),
    peak_xp_level INTEGER NOT NULL DEFAULT 0 CHECK (peak_xp_level >= 0),
    updated_at INTEGER NOT NULL
);
CREATE TABLE leaderboard_spawn_events (
    event_id TEXT PRIMARY KEY,
    player_uuid TEXT NOT NULL,
    recorded_at INTEGER NOT NULL
);
