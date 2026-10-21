-- Schema v2 (Patch v1.2): /spawn cooldown per player, set only after a successful teleport.
-- available_at_epoch_ms is the first moment /spawn is allowed again.

CREATE TABLE spawn_cooldowns (
    player_uuid TEXT PRIMARY KEY,
    available_at_epoch_ms INTEGER NOT NULL
);
