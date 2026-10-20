package de.illunium.custom.appearance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Sole owner of this plugin's nametag and tablist changes; modules only report data. Per online player there is a
 * prefix (nametag and tablist) and a suffix (tablist only). Uses the main scoreboard, so every viewer sees the same,
 * with one team per decorated player. Teams named {@value #TEAM_PREFIX}* belong to this plugin and nothing else.
 * Main thread only. Each change re-renders just that one player.
 */
public final class AppearanceService implements Listener {

    static final String TEAM_PREFIX = "illuniumcustom_";

    private final Server server;
    private final Scoreboard board;
    private final Map<UUID, Component> prefixes = new HashMap<>();
    private final Map<UUID, Component> suffixes = new HashMap<>();

    private AppearanceService(Server server) {
        this.server = server;
        this.board = server.getScoreboardManager().getMainScoreboard();
    }

    public static AppearanceService start(JavaPlugin plugin) {
        AppearanceService appearance = new AppearanceService(plugin.getServer());
        // The main scoreboard is saved with the world, so teams survive a crash; drop ours before rendering.
        appearance.removeOwnTeams();
        plugin.getServer().getPluginManager().registerEvents(appearance, plugin);
        return appearance;
    }

    /** Shown before the name above the head and in the tablist; null removes it. Ignored for offline players. */
    public void setPrefix(UUID player, Component prefix) {
        if (put(prefixes, player, prefix)) {
            render(player);
        }
    }

    /** Shown after the name in the tablist only; null removes it. Ignored for offline players. */
    public void setSuffix(UUID player, Component suffix) {
        if (put(suffixes, player, suffix)) {
            render(player);
        }
    }

    /** Resets every player list name we set and removes our teams. */
    public void close() {
        Set<UUID> decorated = new HashSet<>(prefixes.keySet());
        decorated.addAll(suffixes.keySet());
        prefixes.clear();
        suffixes.clear();
        for (UUID uuid : decorated) {
            Player player = server.getPlayer(uuid);
            if (player != null) {
                player.playerListName(null);
            }
        }
        removeOwnTeams();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        prefixes.remove(uuid);
        suffixes.remove(uuid);
        Team team = board.getTeam(teamName(uuid));
        if (team != null) {
            team.unregister();
        }
    }

    /** Modules report again on join, so nothing is kept for offline players. Returns whether anything changed. */
    private boolean put(Map<UUID, Component> map, UUID player, Component value) {
        if (server.getPlayer(player) == null) {
            return false;
        }
        Component old = value == null ? map.remove(player) : map.put(player, value);
        return !Objects.equals(old, value);
    }

    private void render(UUID uuid) {
        Player player = Objects.requireNonNull(server.getPlayer(uuid));
        Component prefix = prefixes.get(uuid);
        Component suffix = suffixes.get(uuid);

        Team team = board.getTeam(teamName(uuid));
        if (prefix == null) {
            if (team != null) {
                team.unregister();
            }
        } else {
            if (team == null) {
                team = board.registerNewTeam(teamName(uuid));
            }
            team.prefix(prefix);
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
        }

        // The client shows an explicit list name instead of the team decoration, so the prefix is not doubled.
        if (prefix == null && suffix == null) {
            player.playerListName(null);
        } else {
            TextComponent.Builder name = Component.text();
            if (prefix != null) {
                name.append(prefix);
            }
            name.append(Component.text(player.getName()));
            if (suffix != null) {
                name.append(suffix);
            }
            player.playerListName(name.build());
        }
    }

    private void removeOwnTeams() {
        for (Team team : Set.copyOf(board.getTeams())) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }

    private static String teamName(UUID uuid) {
        return TEAM_PREFIX + uuid.toString().replace("-", "");
    }
}
