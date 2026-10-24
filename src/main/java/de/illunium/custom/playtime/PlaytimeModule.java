package de.illunium.custom.playtime;

import static de.illunium.custom.core.command.Chat.error;
import static de.illunium.custom.core.command.Chat.info;

import de.illunium.custom.core.command.Chat;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Playtime since the plugin was first enabled (MVP §4). Sessions are flushed as deltas on quit, every
 * {@value #CHECKPOINT_SECONDS} seconds and on shutdown; a crash loses at most the time since the last checkpoint.
 */
public final class PlaytimeModule implements FeatureModule, Listener, BasicCommand, PlaytimeReadService {

    static final int CHECKPOINT_SECONDS = 60;

    private final PlaytimeTracker tracker = new PlaytimeTracker(System::nanoTime);
    private ModuleContext context;
    private Server server;

    @Override
    public String id() {
        return "playtime";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        this.server = context.plugin().getServer();
        server.getOnlinePlayers().forEach(player -> tracker.start(player.getUniqueId()));
        server.getPluginManager().registerEvents(this, context.plugin());
        long ticks = CHECKPOINT_SECONDS * 20L;
        server.getScheduler().runTaskTimer(context.plugin(), () -> write(tracker.flushAll()), ticks, ticks);
        context.plugin().registerCommand("playtime", "Show playtime", List.of(), this);
    }

    /** Queues the final flush; the database finishes it before closing. */
    @Override
    public void disable() {
        if (server != null) {
            write(tracker.flushAll());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        tracker.start(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        long seconds = tracker.stop(player);
        if (seconds > 0) {
            write(Map.of(player, seconds));
        }
    }

    private void write(Map<UUID, Long> deltas) {
        if (deltas.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        context.database().transaction(c -> add(c, deltas, now)).exceptionally(e -> {
            context.plugin().getLogger().log(Level.SEVERE, "Could not store playtime " + deltas, e);
            return null;
        });
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        try {
            UUID target = args.length > 0 ? resolve(args[0]) : self(sender);
            // Captured now: every flush up to this point is queued before the read, so nothing is missed or doubled.
            long live = tracker.unflushed(target);
            String name = context.identities().name(target).orElse(args.length > 0 ? args[0] : sender.getName());
            context.reply(context.database().transaction(c -> total(c, target)), sender,
                    stored -> sender.sendMessage(info(name + " has played " + PlaytimeTracker.format(stored + live) + ".")));
        } catch (UserException e) {
            sender.sendMessage(error(e.getMessage()));
        }
    }

    @Override
    public boolean available() { return context != null; }

    @Override
    public java.util.concurrent.CompletableFuture<Map<UUID, Long>> snapshot() {
        Map<UUID, Long> live = tracker.snapshot();
        return context.database().transaction(c -> {
            Map<UUID, Long> totals = new java.util.HashMap<>();
            try (PreparedStatement s = c.prepareStatement("SELECT player_uuid, total_seconds FROM playtime");
                 ResultSet rows = s.executeQuery()) {
                while (rows.next()) totals.put(UUID.fromString(rows.getString(1)), rows.getLong(2));
            }
            live.forEach((id, seconds) -> totals.merge(id, seconds, Long::sum));
            return Map.copyOf(totals);
        });
    }

    private UUID resolve(String name) {
        Player online = server.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        List<UUID> known = context.identities().findByName(name);
        if (known.size() != 1) {
            throw new UserException(known.isEmpty() ? "Unknown player " + name + "." : "Several players were last seen as " + name + ".");
        }
        return known.getFirst();
    }

    private static UUID self(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getUniqueId();
        }
        throw new UserException("Usage: /playtime <player>");
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return args.length <= 1 ? Chat.complete(args, server.getOnlinePlayers().stream()
                .filter(p -> context.visibility().isPubliclyVisible(p.getUniqueId())).map(Player::getName)) : List.of();
    }

    @Override
    public String permission() {
        return "illuniumcustom.use";
    }

    static int add(Connection c, Map<UUID, Long> deltas, long now) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO playtime (player_uuid, total_seconds, updated_at) VALUES (?, ?, ?)
                ON CONFLICT (player_uuid) DO UPDATE SET
                    total_seconds = total_seconds + excluded.total_seconds,
                    updated_at = excluded.updated_at""")) {
            for (Map.Entry<UUID, Long> delta : deltas.entrySet()) {
                s.setString(1, delta.getKey().toString());
                s.setLong(2, delta.getValue());
                s.setLong(3, now);
                s.addBatch();
            }
            return s.executeBatch().length;
        }
    }

    static long total(Connection c, UUID player) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT total_seconds FROM playtime WHERE player_uuid = ?")) {
            s.setString(1, player.toString());
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getLong(1) : 0;
            }
        }
    }
}
