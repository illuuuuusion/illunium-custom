package de.illunium.custom.groups;

import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import java.time.InstantSource;
import java.util.List;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerJoinEvent;

/** Player groups with tag, colour and roles (MVP §3). Invites and confirmations are volatile by design. */
public final class GroupsModule implements FeatureModule, Listener {

    private GroupService groups;
    private ModuleContext context;
    private GroupChestService chests;
    private GroupChestInventoryListener chestListener;
    private GroupChatCommand chat;

    @Override
    public String id() {
        return "groups";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        GroupSettings settings = GroupSettings.load(context.settings());
        // Loading reports every member once, which decorates players already online.
        groups = GroupService.load(context.database(), context.mainThread(), InstantSource.system(),
                (player, group) -> context.appearance().setPrefix(player, group == null ? null : group.prefix()));
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        context.plugin().registerCommand("group", "Manage your group", List.of(), new GroupCommand(groups, context));
        context.admin().register("group", new GroupAdminCommand(groups, context));
        if (settings.chestEnabled()) {
            chests = new GroupChestService(context, groups);
            chestListener = new GroupChestInventoryListener(chests);
            context.plugin().getServer().getPluginManager().registerEvents(chestListener, context.plugin());
            context.plugin().registerCommand("gec", "Gemeinsame Gruppenkiste", List.of(), new GroupChestCommand(chests));
        }
        if (settings.chatEnabled()) {
            chat = new GroupChatCommand(groups, context.plugin().getServer(), context.visibility(), settings, System::nanoTime);
            context.plugin().getServer().getPluginManager().registerEvents(chat, context.plugin());
            context.plugin().registerCommand("g", "Privater Gruppenchat", List.of(), chat);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        groups.groupOf(event.getPlayer().getUniqueId())
                .ifPresent(group -> context.appearance().setPrefix(event.getPlayer().getUniqueId(), group.prefix()));
    }

    @Override
    public void disable() {
        if (chests != null) chests.close();
        if (chestListener != null) HandlerList.unregisterAll(chestListener);
        if (chat != null) HandlerList.unregisterAll(chat);
        HandlerList.unregisterAll(this);
    }
}
