package de.illunium.custom.playtimecolors;

import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import de.illunium.custom.playtime.PlaytimeReadService;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Name colour by cumulative {@code /playtime} (Patch v1.5 §3). The tier is derived from the existing playtime account,
 * so nothing is stored. One bulk snapshot per minute (stored totals of all players plus live sessions) is cached and
 * used immediately on join. Hidden (vanished) players get no colour, so no team announces them.
 */
public final class PlaytimeColorsModule implements FeatureModule, Listener {

    static final long REFRESH_TICKS = 60 * 20L;

    private final PlaytimeReadService playtime;
    private ModuleContext context;
    private PlaytimeColorPolicy policy;
    private Map<UUID, Long> totals = Map.of();
    private BukkitTask timer;
    private boolean active;

    public PlaytimeColorsModule(PlaytimeReadService playtime) {
        this.playtime = playtime;
    }

    @Override
    public String id() {
        return "playtimecolors";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        if (context.settings().contains("tiers") && !context.settings().isList("tiers"))
            throw new IllegalArgumentException("playtimecolors.tiers must be a list");
        policy = PlaytimeColorPolicy.parse(context.settings().contains("tiers") ? context.settings().getMapList("tiers") : null);
        if (!playtime.available()) {
            context.plugin().getLogger().warning("Module playtimecolors needs modules.playtime: true; name colours stay off");
            return;
        }
        active = true;
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        context.visibility().onChange(this::render);
        timer = context.plugin().getServer().getScheduler().runTaskTimer(context.plugin(), this::refresh, 0, REFRESH_TICKS);
    }

    private void refresh() {
        playtime.snapshot().whenCompleteAsync((snapshot, error) -> {
            if (!active) return;
            if (error != null) {
                context.plugin().getLogger().log(Level.WARNING, "Could not read playtime for name colours; keeping previous", error);
                return;
            }
            totals = snapshot;
            context.plugin().getServer().getOnlinePlayers().forEach(p -> render(p.getUniqueId()));
        }, context.mainThread());
    }

    private void render(UUID player) {
        if (!active) return; // offline players are ignored by the appearance service
        context.appearance().setNameColor(player, context.visibility().isPubliclyVisible(player)
                ? policy.color(totals.getOrDefault(player, 0L)) : null);
    }

    /** Vanish hides at LOWEST, so visibility is final here. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        render(event.getPlayer().getUniqueId());
    }

    @Override
    public void disable() {
        active = false;
        if (timer != null) timer.cancel();
        HandlerList.unregisterAll(this);
        if (context != null) {
            for (Player player : context.plugin().getServer().getOnlinePlayers()) {
                context.appearance().setNameColor(player.getUniqueId(), null);
            }
        }
    }
}
