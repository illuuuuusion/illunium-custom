package de.illunium.custom.groups;

import static de.illunium.custom.core.command.Chat.arg;
import static de.illunium.custom.core.command.Chat.error;
import static de.illunium.custom.core.command.Chat.info;
import static de.illunium.custom.core.command.Chat.ok;

import de.illunium.custom.core.command.Chat;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.core.module.ModuleContext;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.command.CommandSender;

/** {@code /ic group inspect|setleader|disband}: emergency recovery, gated by {@code illuniumcustom.admin} and logged. */
final class GroupAdminCommand implements BasicCommand {

    private static final String USAGE = "/ic group inspect <group> | setleader <group> <player> | disband <group>";

    private final GroupService groups;
    private final ModuleContext context;

    GroupAdminCommand(GroupService groups, ModuleContext context) {
        this.groups = groups;
        this.context = context;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        try {
            String action = arg(args, 0, USAGE).toLowerCase(Locale.ROOT);
            Group group = groups.byName(arg(args, 1, USAGE))
                    .orElseThrow(() -> new UserException("There is no group named " + args[1] + "."));
            switch (action) {
                case "inspect" -> context.reply(groups.inspect(group.id()), sender, fresh -> fresh.ifPresentOrElse(
                        g -> inspect(sender, g), () -> sender.sendMessage(error("Group no longer exists."))));
                case "setleader" -> {
                    String name = arg(args, 2, USAGE);
                    UUID target = group.members().keySet().stream()
                            .filter(u -> name(u).equalsIgnoreCase(name))
                            .findFirst()
                            .orElseThrow(() -> new UserException(name + " is not a member of " + group.name() + "."));
                    context.reply(groups.adminSetLeader(group.id(), target), sender, g -> {
                        log(sender, "set leader of group " + g.name() + " (#" + g.id() + ") to " + name(target) + " (" + target + ")");
                        sender.sendMessage(ok(name(target) + " now leads " + g.name() + "."));
                    });
                }
                case "disband" -> context.reply(groups.adminDisband(group.id()), sender, g -> {
                    log(sender, "disbanded group " + g.name() + " [" + g.tag() + "] (#" + g.id() + ", " + g.members().size() + " members)");
                    sender.sendMessage(ok("Group " + g.name() + " disbanded."));
                });
                default -> throw new UserException("Usage: " + USAGE);
            }
        } catch (UserException e) {
            sender.sendMessage(error(e.getMessage()));
        }
    }

    private void inspect(CommandSender sender, Group g) {
        sender.sendMessage(info("#" + g.id() + " " + g.name() + " [" + g.tag() + "] colour " + GroupColors.name(g.color())
                + " | leader " + name(g.leader()) + " (" + g.leader() + ")"));
        g.members().forEach((uuid, role) -> sender.sendMessage(info("  " + role + " " + name(uuid) + " (" + uuid + ")")));
        long leaders = g.members().values().stream().filter(r -> r == GroupRole.LEADER).count();
        sender.sendMessage(leaders == 1 ? ok("Invariants OK: exactly one leader, who is a member.")
                : error("Invariant violated: " + leaders + " leaders."));
    }

    private void log(CommandSender sender, String what) {
        context.plugin().getLogger().info("[admin] " + sender.getName() + " " + what);
    }

    private String name(UUID uuid) {
        return context.identities().name(uuid).orElse(uuid.toString());
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return switch (args.length) {
            case 0, 1 -> Chat.complete(args, Stream.of("inspect", "setleader", "disband"));
            case 2 -> Chat.complete(args, groups.all().stream().map(Group::name));
            case 3 -> args[0].equalsIgnoreCase("setleader")
                    ? Chat.complete(args, groups.byName(args[1]).stream().flatMap(g -> g.members().keySet().stream()).map(this::name))
                    : List.of();
            default -> List.of();
        };
    }
}
