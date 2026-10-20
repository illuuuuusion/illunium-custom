package de.illunium.custom.groups;

import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import java.time.InstantSource;
import java.util.List;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Player groups with tag, colour and roles (MVP §3). Invites and confirmations are volatile by design. */
public final class GroupsModule implements FeatureModule, Listener {

    private GroupService groups;
    private ModuleContext context;

    @Override
    public String id() {
        return "groups";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        // Loading reports every member once, which decorates players already online.
        groups = GroupService.load(context.database(), context.mainThread(), InstantSource.system(),
                (player, group) -> context.appearance().setPrefix(player, group == null ? null : group.prefix()));
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        context.plugin().registerCommand("group", "Manage your group", List.of(), new GroupCommand(groups, context));
        context.admin().register("group", new GroupAdminCommand(groups, context));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        groups.groupOf(event.getPlayer().getUniqueId())
                .ifPresent(group -> context.appearance().setPrefix(event.getPlayer().getUniqueId(), group.prefix()));
    }

    @Override
    public void disable() {
        // All state is committed per command; pending invites/confirmations are dropped on purpose.
    }
}
