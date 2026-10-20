package de.illunium.custom.sit;

import static de.illunium.custom.core.command.Chat.error;
import static de.illunium.custom.core.command.Chat.info;

import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code /sit} and {@code /unsit} (MVP §7). A player sits on an invisible marker armor stand that is tagged as ours
 * and never saved with the chunk. A player is SITTING exactly while they have an entry in {@link #seats}; every way out
 * goes through {@link #standUp}, which removes the seat. Seats left behind by a crash (e.g. restored as a player's
 * vehicle) are removed on start, on entity load and on join. Main thread only.
 */
public final class SitModule implements FeatureModule, Listener {

    private record Seat(ArmorStand stand, Location standingSpot) {
    }

    private final Map<UUID, Seat> seats = new HashMap<>();
    private JavaPlugin plugin;
    private Server server;
    private NamespacedKey seatKey;

    @Override
    public String id() {
        return "sit";
    }

    @Override
    public void enable(ModuleContext context) {
        plugin = context.plugin();
        server = plugin.getServer();
        seatKey = new NamespacedKey(plugin, "seat");
        // Only entities already loaded; anything in unloaded chunks is caught by onEntitiesLoad.
        for (World world : server.getWorlds()) {
            world.getEntitiesByClass(ArmorStand.class).stream().filter(this::isSeat).forEach(Entity::remove);
        }
        server.getPluginManager().registerEvents(this, plugin);
        plugin.registerCommand("sit", "Sit down or stand up", List.of(), new SitCommand(true));
        plugin.registerCommand("unsit", "Stand up", List.of(), new SitCommand(false));
    }

    @Override
    public void disable() {
        if (server != null) {
            List.copyOf(seats.keySet()).forEach(player -> standUp(player, false));
        }
    }

    /** Why {@code player} cannot sit where they are, or null if they can. */
    static String problem(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return "Spectators can't sit.";
        }
        if (player.isInsideVehicle()) {
            return "You are already riding something.";
        }
        if (player.isFlying() || player.isGliding() || player.isRiptiding()) {
            return "You can't sit while flying.";
        }
        if (player.isSwimming() || player.isInWater() || player.isInLava()) {
            return "You can't sit in liquids.";
        }
        if (player.isSleeping()) {
            return "You can't sit in bed.";
        }
        // Checked on the server's block data, not the client-reported on-ground flag: just below the feet must be a
        // block with collision (full block, slab, stair, carpet, ...). Falling or jumping players stand on air.
        Block below = player.getLocation().subtract(0, 0.05, 0).getBlock();
        if (below.isPassable() || below.isLiquid()) {
            return "You need to stand on solid ground.";
        }
        return null;
    }

    private void sit(Player player) {
        Location spot = player.getLocation();
        ArmorStand stand = player.getWorld().spawn(spot, ArmorStand.class, s -> {
            s.setMarker(true);
            s.setInvisible(true);
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setPersistent(false);
            s.getPersistentDataContainer().set(seatKey, PersistentDataType.BOOLEAN, true);
        });
        if (!stand.addPassenger(player)) {
            stand.remove();
            player.sendMessage(error("You can't sit here."));
            return;
        }
        seats.put(player.getUniqueId(), new Seat(stand, spot));
    }

    /**
     * Ends the session and removes the seat. With {@code restore} the player is put back on the spot they sat down on
     * (next tick, after vanilla has placed the dismounted player next to the seat). Returns whether they were sitting.
     */
    private boolean standUp(UUID uuid, boolean restore) {
        Seat seat = seats.remove(uuid);
        if (seat == null) {
            return false;
        }
        seat.stand().remove();
        if (restore) {
            server.getScheduler().runTask(plugin, () -> {
                Player player = server.getPlayer(uuid);
                Location spot = seat.standingSpot();
                if (player != null && !player.isDead() && !player.isInsideVehicle() && player.getWorld().equals(spot.getWorld())
                        && player.getLocation().distanceSquared(spot) < 4) {
                    player.teleport(spot.clone().setRotation(player.getYaw(), player.getPitch()));
                }
            });
        }
        return true;
    }

    private boolean isSeat(Entity entity) {
        return entity instanceof ArmorStand && entity.getPersistentDataContainer().has(seatKey);
    }

    private boolean isActiveSeat(Entity entity) {
        return seats.values().stream().anyMatch(seat -> seat.stand().equals(entity));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        Seat seat = seats.get(event.getEntity().getUniqueId());
        if (seat != null && seat.stand().equals(event.getDismounted())) {
            standUp(event.getEntity().getUniqueId(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        standUp(event.getEntity().getUniqueId(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        standUp(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        standUp(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        standUp(event.getPlayer().getUniqueId(), false);
    }

    /** Before the player is saved, so the seat is not stored as their vehicle. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        standUp(event.getPlayer().getUniqueId(), false);
    }

    /** A crash can save the seat inside the player's data, which respawns it as their vehicle on join. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Entity vehicle = event.getPlayer().getVehicle();
        if (vehicle != null && isSeat(vehicle)) {
            vehicle.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        event.getEntities().stream().filter(e -> isSeat(e) && !isActiveSeat(e)).forEach(Entity::remove);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isSeat(entity)) {
                seats.values().removeIf(seat -> seat.stand().equals(entity));
                entity.remove();
            }
        }
    }

    private final class SitCommand implements BasicCommand {

        private final boolean toggle;

        SitCommand(boolean toggle) {
            this.toggle = toggle;
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            if (!(source.getSender() instanceof Player player)) {
                source.getSender().sendMessage(error("Only players can do that."));
                return;
            }
            if (standUp(player.getUniqueId(), true)) {
                return;
            }
            if (!toggle) {
                player.sendMessage(info("You are not sitting."));
                return;
            }
            String problem = problem(player);
            if (problem != null) {
                player.sendMessage(error(problem));
            } else {
                sit(player);
            }
        }

        @Override
        public String permission() {
            return "illuniumcustom.use";
        }
    }
}
