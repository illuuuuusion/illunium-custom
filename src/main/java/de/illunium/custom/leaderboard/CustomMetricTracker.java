package de.illunium.custom.leaderboard;

import de.illunium.custom.leaderboard.LeaderboardRepository.Metrics;
import de.illunium.custom.spawn.SpawnTeleportSucceeded;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Main-thread deltas survive a failed transaction and are retried at the next checkpoint. */
final class CustomMetricTracker implements Listener {
    private final LeaderboardRepository repository;
    private final Logger logger;
    private Map<UUID, Metrics> pending = new HashMap<>(), writing = Map.of();
    private Map<UUID, UUID> events = new HashMap<>(), writingEvents = Map.of();
    private CompletableFuture<Void> inFlight;

    CustomMetricTracker(LeaderboardRepository repository, Logger logger) { this.repository = repository; this.logger = logger; }
    void add(UUID player, Metrics metrics) { pending.merge(player, metrics, Metrics::plus); }
    void peak(Player player) { add(player.getUniqueId(), new Metrics(0, 0, 0, player.getLevel())); }

    void flush() {
        if (inFlight != null) {
            if (!inFlight.isDone()) return;
            settle();
        }
        if (pending.isEmpty() && events.isEmpty()) return;
        writing = Map.copyOf(pending); pending.clear();
        writingEvents = Map.copyOf(events); events.clear();
        inFlight = repository.save(writing, writingEvents);
    }

    private void settle() {
        try { inFlight.join(); }
        catch (RuntimeException e) {
            writing.forEach((id, delta) -> pending.merge(id, delta, Metrics::plus));
            events.putAll(writingEvents);
            logger.log(Level.SEVERE, "Could not store leaderboard metrics; retained for retry", e);
        }
        inFlight = null; writing = Map.of(); writingEvents = Map.of();
    }

    void close() {
        if (inFlight != null) settle();
        for (int attempt = 0; attempt < 3 && (!pending.isEmpty() || !events.isEmpty()); attempt++) {
            flush(); settle();
        }
        if (!pending.isEmpty() || !events.isEmpty()) logger.severe("Leaderboard shutdown: uncommitted metrics remain; database unavailable");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled() || !event.canBuild()) return;
        GameMode mode = event.getPlayer().getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) return;
        long count = event instanceof BlockMultiPlaceEvent multi ? multi.getReplacedBlockStates().stream()
                .map(s -> s.getLocation()).distinct().count() : 1;
        add(event.getPlayer().getUniqueId(), new Metrics(count, 0, 0, 0));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent event) {
        if (!event.isCancelled() && event.getHand() != null && event.getEntity() instanceof Player player) {
            add(player.getUniqueId(), new Metrics(0, 1, 0, 0)); flush();
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawn(SpawnTeleportSucceeded event) { events.putIfAbsent(event.session(), event.player()); flush(); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { peak(event.getPlayer()); flush(); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevel(PlayerLevelChangeEvent event) {
        add(event.getPlayer().getUniqueId(), new Metrics(0, 0, 0, Math.max(event.getOldLevel(), event.getNewLevel()))); flush();
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) { peak(event.getPlayer()); flush(); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { peak(event.getPlayer()); flush(); }
}
