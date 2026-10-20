package de.illunium.custom.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Server;

/**
 * Whether other players may know that someone is online. Only the vanish module hides anyone; without it everybody
 * online is publicly visible. Modules ask here instead of {@code Server#getPlayer} before showing presence (hearts,
 * completions, "online" texts). Main thread only, except the read-only {@link #hidden()} (server list pings).
 */
public final class VisibilityService {

    private final Server server;
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    private final List<Consumer<UUID>> listeners = new ArrayList<>();

    public VisibilityService(Server server) {
        this.server = server;
    }

    public boolean isPubliclyVisible(UUID player) {
        return !hidden.contains(player);
    }

    public boolean publiclyOnline(UUID player) {
        return isPubliclyVisible(player) && server.getPlayer(player) != null;
    }

    /** Called with the player whenever their visibility changed (not on plain join/quit). */
    public void onChange(Consumer<UUID> listener) {
        listeners.add(listener);
    }

    /** For the vanish module. Returns whether the state changed; listeners run only then. */
    public boolean setHidden(UUID player, boolean hide) {
        if (!(hide ? hidden.add(player) : hidden.remove(player))) {
            return false;
        }
        listeners.forEach(listener -> listener.accept(player));
        return true;
    }

    /** Drops a quitting player without notifying anyone; they are offline, not visible. */
    public void forget(UUID player) {
        hidden.remove(player);
    }

    /** Hidden players (all online). */
    public Set<UUID> hidden() {
        return Set.copyOf(hidden);
    }
}
