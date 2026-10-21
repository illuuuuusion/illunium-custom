package de.illunium.custom.spawn;

import static de.illunium.custom.core.command.Chat.error;
import static de.illunium.custom.core.command.Chat.info;
import static de.illunium.custom.core.command.Chat.ok;

import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * {@code /spawn}: teleports to the world spawn that vanilla {@code /setworldspawn} manages, read on every use; never a
 * personal bed or anchor spawn. Rules (warmup, combat tag, cooldown) live in {@link SpawnService}; this class wires
 * them to Paper events. A blocked spawn is refused instead of teleporting into blocks. A sitting player is unseated by
 * the sit module's teleport listener. Cooldowns are loaded once on start and written through the database queue.
 */
public final class SpawnModule implements FeatureModule, Listener {

    private final SpawnService spawns = new SpawnService(() -> Instant.EPOCH.plusNanos(System.nanoTime()), InstantSource.system());
    private ModuleContext context;
    private Server server;
    private BukkitTask timer;

    @Override
    public String id() {
        return "spawn";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        server = context.plugin().getServer();
        Objects.requireNonNull(server.getRespawnWorld(), "no spawn world is loaded");
        // Startup may block on storage; expired rows are dropped on the way.
        context.database().transaction(c -> loadCooldowns(c, System.currentTimeMillis())).join().forEach(spawns::loadCooldown);
        server.getPluginManager().registerEvents(this, context.plugin());
        timer = server.getScheduler().runTaskTimer(context.plugin(), this::tick, 1, 1);
        context.plugin().registerCommand("spawn", "Teleport to the world spawn", List.of(), new SpawnCommand());
    }

    /** Running warmups just end without cooldown. Active cooldowns are queued again, retrying any failed write. */
    @Override
    public void disable() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (server != null) {
            spawns.warmingUp().forEach(spawns::cancel);
            persist(spawns.activeCooldowns());
        }
    }

    /** Every tick: finish due warmups; refresh the countdown in the action bar twice a second. */
    private void tick() {
        spawns.due().forEach(this::complete);
        if (server.getCurrentTick() % 10 == 0) {
            for (UUID uuid : spawns.warmingUp()) {
                Player player = server.getPlayer(uuid);
                long left = SpawnService.seconds(spawns.warmupLeft(uuid));
                if (player != null && left > 0) {
                    player.sendActionBar(info("Teleport to spawn in " + left + " s"));
                }
            }
        }
    }

    private void complete(UUID uuid) {
        Player player = server.getPlayer(uuid);
        if (player == null) {
            spawns.cancel(uuid);
            return;
        }
        Location spawn = server.getRespawnWorld().getSpawnLocation();
        // Movement or damage while the chunk loads still cancels: the warmup is only closed by beginTeleport.
        spawn.getWorld().getChunkAtAsync(spawn).thenAccept(chunk -> {
            if (!player.isConnected() || !spawns.beginTeleport(uuid)) {
                return;
            }
            Location target = safeSpot(spawn);
            Instant until = spawns.finishTeleport(uuid, target != null && player.teleport(target, PlayerTeleportEvent.TeleportCause.COMMAND));
            player.sendActionBar(Component.empty());
            if (until == null) {
                player.sendMessage(error(target == null ? "The spawn is blocked right now. Please tell an admin." : "Teleport was cancelled."));
                return;
            }
            player.sendMessage(ok("Teleported to spawn."));
            persist(Map.of(uuid, until)).whenCompleteAsync((stored, e) -> player.sendMessage(e == null
                    ? info("Next /spawn in " + SpawnService.COOLDOWN.toMinutes() + " minutes.")
                    : error("Your cooldown could not be saved; /spawn stays locked anyway. Please tell an admin.")), context.mainThread());
        }).exceptionally(e -> {
            context.plugin().getLogger().log(Level.SEVERE, "Spawn teleport of " + player.getName() + " failed", e);
            context.mainThread().execute(() -> {
                spawns.cancel(uuid);
                spawns.finishTeleport(uuid, false);
            });
            return null;
        });
    }

    /** Fail-closed: a failed write is logged, the cooldown stays active in memory and is retried on disable. */
    private CompletableFuture<Integer> persist(Map<UUID, Instant> cooldowns) {
        if (cooldowns.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        return context.database().transaction(c -> saveCooldowns(c, cooldowns)).whenComplete((stored, e) -> {
            if (e != null) {
                context.plugin().getLogger().log(Level.SEVERE, "Could not store spawn cooldowns " + cooldowns, e);
            }
        });
    }

    private void cancel(Player player, String reason) {
        if (spawns.cancel(player.getUniqueId())) {
            player.sendActionBar(Component.empty());
            player.sendMessage(error("Teleport to spawn cancelled: " + reason + "."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition() && spawns.moved(event.getPlayer().getUniqueId(), event.getTo())) {
            cancel(event.getPlayer(), "you moved");
        }
    }

    /** Our own final teleport runs in COMPLETING, which {@link SpawnService#cancel} leaves alone. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        cancel(event.getPlayer(), "you were teleported");
    }

    /**
     * Any effective damage cancels the warmup. If another player caused it (melee, or projectile etc. as attributed by
     * Paper's damage source), both are tagged and the attacker's warmup ends too. Also receives
     * {@code EntityDamageByEntityEvent}, so each hit is handled exactly once.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0) {
            return;
        }
        cancel(victim, "you took damage");
        if (event.getDamageSource().getCausingEntity() instanceof Player attacker && !attacker.equals(victim)) {
            spawns.tag(attacker.getUniqueId());
            spawns.tag(victim.getUniqueId());
            cancel(attacker, "you are in combat");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        cancel(event.getPlayer(), "you died");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        cancel(event.getPlayer(), "you changed worlds");
    }

    /** No penalty: the warmup and the combat tag are simply dropped, so a relog clears the tag (accepted in v1.2). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        spawns.forget(event.getPlayer().getUniqueId());
    }

    /** The spawn block if a player fits there, else the top of that column, else null. Chunk must be loaded. */
    static Location safeSpot(Location spawn) {
        Location spot = spawn.toBlockLocation().add(0.5, 0, 0.5).setRotation(spawn.getYaw(), spawn.getPitch());
        if (fits(spot.getBlock())) {
            return spot;
        }
        Block top = spawn.getWorld().getHighestBlockAt(spot).getRelative(BlockFace.UP);
        return fits(top) ? top.getLocation().add(0.5, 0, 0.5).setRotation(spawn.getYaw(), spawn.getPitch()) : null;
    }

    private static boolean fits(Block feet) {
        return standable(feet.getRelative(BlockFace.DOWN), feet, feet.getRelative(BlockFace.UP));
    }

    /** Solid ground, then two free blocks that are neither liquid nor fire. */
    static boolean standable(Block below, Block feet, Block head) {
        return !below.isPassable() && !below.isLiquid() && free(feet) && free(head);
    }

    private static boolean free(Block block) {
        return block.isPassable() && !block.isLiquid() && block.getType() != Material.FIRE && block.getType() != Material.SOUL_FIRE;
    }

    /** Drops expired rows and returns the rest. */
    static Map<UUID, Instant> loadCooldowns(Connection c, long nowMs) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("DELETE FROM spawn_cooldowns WHERE available_at_epoch_ms <= ?")) {
            s.setLong(1, nowMs);
            s.executeUpdate();
        }
        Map<UUID, Instant> cooldowns = new HashMap<>();
        try (PreparedStatement s = c.prepareStatement("SELECT player_uuid, available_at_epoch_ms FROM spawn_cooldowns");
             ResultSet r = s.executeQuery()) {
            while (r.next()) {
                cooldowns.put(UUID.fromString(r.getString(1)), Instant.ofEpochMilli(r.getLong(2)));
            }
        }
        return cooldowns;
    }

    static int saveCooldowns(Connection c, Map<UUID, Instant> cooldowns) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO spawn_cooldowns (player_uuid, available_at_epoch_ms) VALUES (?, ?)
                ON CONFLICT (player_uuid) DO UPDATE SET available_at_epoch_ms = excluded.available_at_epoch_ms""")) {
            for (Map.Entry<UUID, Instant> cooldown : cooldowns.entrySet()) {
                s.setString(1, cooldown.getKey().toString());
                s.setLong(2, cooldown.getValue().toEpochMilli());
                s.addBatch();
            }
            return s.executeBatch().length;
        }
    }

    private final class SpawnCommand implements BasicCommand {

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            if (!(source.getSender() instanceof Player player)) {
                source.getSender().sendMessage(error("Only players can do that."));
                return;
            }
            UUID uuid = player.getUniqueId();
            String problem = spawns.startProblem(uuid);
            if (problem != null) {
                player.sendMessage(error(problem));
                return;
            }
            Location spawn = server.getRespawnWorld().getSpawnLocation();
            // Loads just the spawn chunk off-thread; the callback runs on the main thread.
            spawn.getWorld().getChunkAtAsync(spawn).thenAccept(chunk -> {
                if (!player.isConnected()) {
                    return;
                }
                // Checked again: a second /spawn or a PvP hit may have come in while the chunk loaded.
                String late = spawns.startProblem(uuid);
                if (late != null) {
                    player.sendMessage(error(late));
                } else if (safeSpot(spawn) == null) {
                    player.sendMessage(error("The spawn is blocked right now. Please tell an admin."));
                } else {
                    spawns.start(uuid, player.getLocation());
                    player.sendMessage(info("Teleporting to spawn in " + SpawnService.WARMUP.toSeconds() + " seconds. Don't move!"));
                }
            });
        }

        @Override
        public String permission() {
            return "illuniumcustom.use";
        }
    }
}
