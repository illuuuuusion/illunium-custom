package de.illunium.custom.groups;

import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import java.time.InstantSource;
import java.util.List;

/** Player groups with tag, colour and roles (MVP §3). Invites and confirmations are volatile by design. */
public final class GroupsModule implements FeatureModule {

    @Override
    public String id() {
        return "groups";
    }

    @Override
    public void enable(ModuleContext context) {
        GroupService groups = GroupService.load(context.database(), context.mainThread(), InstantSource.system(), (player, group) -> {
        });
        context.plugin().registerCommand("group", "Manage your group", List.of(), new GroupCommand(groups, context));
        context.admin().register("group", new GroupAdminCommand(groups, context));
    }

    @Override
    public void disable() {
        // All state is committed per command; pending invites/confirmations are dropped on purpose.
    }
}
