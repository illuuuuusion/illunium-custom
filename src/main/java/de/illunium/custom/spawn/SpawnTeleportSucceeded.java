package de.illunium.custom.spawn;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Published only after a completed /spawn teleport, independently of any statistics consumer. */
public final class SpawnTeleportSucceeded extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID player;
    private final UUID session;

    public SpawnTeleportSucceeded(UUID player, UUID session) {
        this.player = player;
        this.session = session;
    }

    public UUID player() { return player; }
    public UUID session() { return session; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
