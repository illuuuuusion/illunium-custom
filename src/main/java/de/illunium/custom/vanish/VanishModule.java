package de.illunium.custom.vanish;

import static de.illunium.custom.core.command.Chat.error;
import static de.illunium.custom.core.command.Chat.info;
import static de.illunium.custom.core.command.Chat.ok;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import de.illunium.custom.core.VisibilityService;
import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Vanilla operators join hidden: no join message, no tablist entry and no entity for any other player, until
 * {@code /unvanish}; {@code /vanish} hides them again with a vanilla-style leave message. Only {@link Player#isOp()}
 * counts, checked on every use. The state lives in {@link VisibilityService} and is volatile on purpose, so each
 * login follows the configured UUID join default. Hiding is per viewer via {@code hidePlayer}; names and tags stay with the appearance service.
 * Main thread only.
 */
public final class VanishModule implements FeatureModule, Listener {

    private JavaPlugin plugin;
    private Server server;
    private VisibilityService visibility;
    private BukkitTask opCheck;
    private AutoVanishPolicy policy;

    @Override
    public String id() {
        return "vanish";
    }

    @Override
    public void enable(ModuleContext context) {
        policy = AutoVanishPolicy.load(context.settings());
        plugin = context.plugin();
        server = plugin.getServer();
        visibility = context.visibility();
        // Without hot reload this only happens after a failed start; an operator already online counts as just joined.
        server.getOnlinePlayers().stream().filter(p -> policy.hidden(p.isOp(), p.getUniqueId())).forEach(this::hide);
        server.getPluginManager().registerEvents(this, plugin);
        plugin.registerCommand("vanish", "Hide yourself from other players", List.of(), new VanishCommand(true));
        plugin.registerCommand("unvanish", "Show yourself to other players", List.of(), new VanishCommand(false));
        // There is no de-op event: a hidden player who lost OP becomes visible within a second.
        opCheck = server.getScheduler().runTaskTimer(plugin, this::revalidate, 20, 20);
    }

    @Override
    public void disable() {
        if (opCheck != null) {
            opCheck.cancel();
        }
        if (visibility != null) {
            for (UUID uuid : visibility.hidden()) {
                Player admin = server.getPlayer(uuid);
                if (admin != null) {
                    show(admin);
                } else {
                    visibility.forget(uuid);
                }
            }
        }
    }

    private boolean hide(Player admin) {
        if (!visibility.setHidden(admin.getUniqueId(), true)) {
            return false;
        }
        for (Player viewer : server.getOnlinePlayers()) {
            if (viewer != admin) {
                viewer.hidePlayer(plugin, admin);
            }
        }
        return true;
    }

    private boolean show(Player admin) {
        if (!visibility.setHidden(admin.getUniqueId(), false)) {
            return false;
        }
        for (Player viewer : server.getOnlinePlayers()) {
            viewer.showPlayer(plugin, admin);
        }
        return true;
    }

    private void revalidate() {
        for (UUID uuid : visibility.hidden()) {
            Player admin = server.getPlayer(uuid);
            if (admin != null && !admin.isOp() && show(admin)) {
                admin.sendMessage(info("You are no longer an operator and are now visible."));
            }
        }
    }

    /** First, so the server never announces a hidden player: the join broadcast and tablist entry honour canSee. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (UUID uuid : visibility.hidden()) {
            Player admin = server.getPlayer(uuid);
            if (admin != null && admin != player) {
                player.hidePlayer(plugin, admin);
            }
        }
        if (policy.hidden(player.isOp(), player.getUniqueId())) {
            hide(player);
            event.joinMessage(null);
        }
    }

    /** Silently forgotten: the player is offline anyway and must not flash as "visible" to the other modules. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!visibility.isPubliclyVisible(uuid)) {
            event.quitMessage(null);
            visibility.forget(uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!visibility.isPubliclyVisible(event.getPlayer().getUniqueId())) {
            event.deathMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!visibility.isPubliclyVisible(event.getPlayer().getUniqueId())) {
            event.message(null);
        }
    }

    /** Server list: hidden players are neither counted nor listed. May run off the main thread. */
    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        Set<UUID> hidden = visibility.hidden();
        if (!hidden.isEmpty()) {
            event.getListedPlayers().removeIf(listed -> hidden.contains(listed.id()));
            event.setNumPlayers(Math.max(0, event.getNumPlayers() - hidden.size()));
        }
    }

    private final class VanishCommand implements BasicCommand {

        private final boolean hide;

        VanishCommand(boolean hide) {
            this.hide = hide;
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            if (!(source.getSender() instanceof Player player) || !player.isOp()) {
                source.getSender().sendMessage(error("Only operators in game can do that."));
                return;
            }
            if (!hide) {
                player.sendMessage(show(player) ? ok("You are now visible.") : info("You are already visible."));
            } else if (hide(player)) {
                server.broadcast(Component.translatable("multiplayer.player.left", NamedTextColor.YELLOW, player.displayName()));
                player.sendMessage(ok("You are now hidden."));
            } else {
                player.sendMessage(info("You are already hidden."));
            }
        }

        /** Operators only; no Bukkit permission grants this. The console may run it to get a clear error. */
        @Override
        public boolean canUse(CommandSender sender) {
            return !(sender instanceof Player player) || player.isOp();
        }
    }
}
