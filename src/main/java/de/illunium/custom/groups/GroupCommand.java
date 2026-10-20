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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /group ...}: parses arguments, resolves names and reports results; all rules live in {@link GroupService}. */
final class GroupCommand implements BasicCommand {

    private static final List<String> SUBCOMMANDS = List.of("create", "list", "info", "members", "invite", "uninvite",
            "accept", "deny", "kick", "promote", "demote", "rename", "tag", "color", "transfer", "disband", "confirm",
            "cancel", "leave");

    private final GroupService groups;
    private final ModuleContext context;
    private final Server server;

    GroupCommand(GroupService groups, ModuleContext context) {
        this.groups = groups;
        this.context = context;
        this.server = context.plugin().getServer();
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "list" -> list(sender);
                case "info" -> showInfo(sender, args.length > 1 ? groupByName(args[1]) : own(sender));
                default -> {
                    if (!SUBCOMMANDS.contains(sub)) {
                        sender.sendMessage(info("Usage: /group <" + String.join("|", SUBCOMMANDS) + ">"));
                    } else if (sender instanceof Player player) {
                        playerCommand(player, sub, args);
                    } else {
                        sender.sendMessage(error("/group " + sub + " can only be used by players."));
                    }
                }
            }
        } catch (UserException e) {
            sender.sendMessage(error(e.getMessage()));
        }
    }

    private void playerCommand(Player player, String sub, String[] args) {
        UUID actor = player.getUniqueId();
        switch (sub) {
            case "create" -> {
                String name = arg(args, 1, "/group create <name> <tag>");
                String tag = arg(args, 2, "/group create <name> <tag>");
                reply(player, groups.create(actor, name, tag), g -> ok("Created group " + g.name() + " [" + g.tag() + "]."));
            }
            case "members" -> members(player, own(player));
            case "invite" -> {
                Player target = server.getPlayerExact(arg(args, 1, "/group invite <player>"));
                if (target == null || !context.visibility().isPubliclyVisible(target.getUniqueId())) {
                    throw new UserException("That player is not online.");
                }
                reply(player, groups.invite(actor, target.getUniqueId()), g -> {
                    target.sendMessage(info(player.getName() + " invited you to " + g.name() + " [" + g.tag() + "]. ")
                            .append(Component.text("[Accept]", NamedTextColor.GREEN)
                                    .clickEvent(ClickEvent.runCommand("/group accept " + g.name())))
                            .append(info(" (expires in 5 minutes)")));
                    return ok("Invited " + target.getName() + ".");
                });
            }
            case "uninvite" -> {
                Group group = own(player);
                UUID target = byName(groups.invitees(group.id()), arg(args, 1, "/group uninvite <player>"), "has no pending invite.");
                reply(player, groups.uninvite(actor, target), g -> ok("Invite withdrawn."));
            }
            case "accept" -> reply(player, groups.accept(actor, arg(args, 1, "/group accept <group>")), g -> {
                notifyMembers(g, info(player.getName() + " joined the group."), actor);
                return ok("You joined " + g.name() + ".");
            });
            case "deny" -> {
                Group g = groups.deny(actor, arg(args, 1, "/group deny <group>"));
                player.sendMessage(ok("Invite from " + g.name() + " declined."));
            }
            case "kick" -> {
                UUID target = member(player, args, "/group kick <player>");
                reply(player, groups.kick(actor, target), g -> {
                    message(target, info("You were kicked from " + g.name() + "."));
                    return ok("Kicked " + name(target) + ".");
                });
            }
            case "promote" -> {
                UUID target = member(player, args, "/group promote <player>");
                reply(player, groups.promote(actor, target), g -> {
                    message(target, info("You are now a mod of " + g.name() + "."));
                    return ok(name(target) + " is now a mod.");
                });
            }
            case "demote" -> {
                UUID target = member(player, args, "/group demote <player>");
                reply(player, groups.demote(actor, target), g -> {
                    message(target, info("You are no longer a mod of " + g.name() + "."));
                    return ok(name(target) + " is now a member.");
                });
            }
            case "rename" -> reply(player, groups.rename(actor, arg(args, 1, "/group rename <name>")),
                    g -> ok("Group renamed to " + g.name() + "."));
            case "tag" -> reply(player, groups.retag(actor, arg(args, 1, "/group tag <tag>")),
                    g -> ok("Tag changed to [" + g.tag() + "]."));
            case "color" -> reply(player, groups.recolor(actor, arg(args, 1, "/group color <color|#RRGGBB>")),
                    g -> ok("Colour changed.").append(Component.text(" [" + g.tag() + "]", g.color())));
            case "transfer" -> {
                UUID target = member(player, args, "/group transfer <player>");
                reply(player, groups.requestTransfer(actor, target),
                        g -> info("Transfer leadership of " + g.name() + " to " + name(target)
                                + "? Type /group confirm within 30 seconds or /group cancel."));
            }
            case "disband" -> reply(player, groups.requestDisband(actor),
                    g -> info("Disband " + g.name() + " for all members? Type /group confirm within 30 seconds or /group cancel."));
            case "confirm" -> reply(player, groups.confirm(actor), done -> switch (done.action()) {
                case GroupService.Transfer t -> {
                    message(t.target(), ok("You are now the leader of " + done.group().name() + "."));
                    yield ok(name(t.target()) + " now leads " + done.group().name() + "; you are a mod.");
                }
                case GroupService.Disband d -> {
                    notifyMembers(done.group(), info("Group " + done.group().name() + " was disbanded."), actor);
                    yield ok("Group " + done.group().name() + " disbanded.");
                }
            });
            case "cancel" -> {
                groups.cancel(actor);
                player.sendMessage(ok("Cancelled."));
            }
            case "leave" -> reply(player, groups.leave(actor), g -> {
                notifyMembers(g, info(player.getName() + " left the group."), actor);
                return ok("You left " + g.name() + ".");
            });
            default -> throw new IllegalStateException(sub);
        }
    }

    private void list(CommandSender sender) {
        List<Group> all = groups.all().stream().sorted(Comparator.comparing(g -> g.name().toLowerCase(Locale.ROOT))).toList();
        if (all.isEmpty()) {
            sender.sendMessage(info("There are no groups yet. Create one with /group create <name> <tag>."));
            return;
        }
        Component line = info("Groups (" + all.size() + "): ");
        for (int i = 0; i < all.size(); i++) {
            Group g = all.get(i);
            line = line.append(g.prefix()).append(Component.text(g.name() + " (" + g.members().size() + ")"
                    + (i < all.size() - 1 ? ", " : ""), NamedTextColor.WHITE));
        }
        sender.sendMessage(line);
    }

    private void showInfo(CommandSender sender, Group g) {
        sender.sendMessage(g.prefix().append(Component.text(g.name(), NamedTextColor.WHITE)));
        sender.sendMessage(info("Colour: " + GroupColors.name(g.color()) + " | Leader: " + name(g.leader())
                + " | Members: " + g.members().size()));
    }

    private void members(CommandSender sender, Group g) {
        showInfo(sender, g);
        for (GroupRole role : GroupRole.values()) {
            String names = g.members().entrySet().stream()
                    .filter(e -> e.getValue() == role)
                    .map(e -> name(e.getKey()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.joining(", "));
            if (!names.isEmpty()) {
                sender.sendMessage(info(role.name().charAt(0) + role.name().substring(1).toLowerCase(Locale.ROOT) + ": " + names));
            }
        }
    }

    private <T> void reply(Player player, CompletableFuture<T> result, Function<T, Component> success) {
        context.reply(result, player, value -> player.sendMessage(success.apply(value)));
    }

    private Group own(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new UserException("Usage: /group info <group>");
        }
        return groups.groupOf(player.getUniqueId()).orElseThrow(() -> new UserException("You are not in a group."));
    }

    private Group groupByName(String name) {
        return groups.byName(name).orElseThrow(() -> new UserException("There is no group named " + name + "."));
    }

    private UUID member(Player player, String[] args, String usage) {
        String name = arg(args, 1, usage);
        return byName(own(player).members().keySet(), name, "is not in your group.");
    }

    /** The one candidate whose last known name is {@code name}. */
    private UUID byName(Collection<UUID> candidates, String name, String notFound) {
        List<UUID> matches = candidates.stream().filter(u -> name(u).equalsIgnoreCase(name)).toList();
        if (matches.size() != 1) {
            throw new UserException(matches.isEmpty() ? name + " " + notFound : "Several players were last seen as " + name + ".");
        }
        return matches.getFirst();
    }

    private String name(UUID uuid) {
        return context.identities().name(uuid).orElse(uuid.toString());
    }

    private void message(UUID uuid, Component message) {
        Player player = server.getPlayer(uuid);
        if (player != null) {
            player.sendMessage(message);
        }
    }

    private void notifyMembers(Group group, Component message, UUID except) {
        group.members().keySet().stream().filter(u -> !u.equals(except)).forEach(u -> message(u, message));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            return Chat.complete(args, SUBCOMMANDS.stream());
        }
        if (args.length > 2) {
            return List.of();
        }
        UUID self = source.getSender() instanceof Player p ? p.getUniqueId() : null;
        Group own = self == null ? null : groups.groupOf(self).orElse(null);
        Stream<String> candidates = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> groups.all().stream().map(Group::name);
            case "invite" -> server.getOnlinePlayers().stream()
                    .filter(p -> !p.getUniqueId().equals(self) && groups.groupOf(p.getUniqueId()).isEmpty()
                            && context.visibility().isPubliclyVisible(p.getUniqueId()))
                    .map(Player::getName);
            case "uninvite" -> own == null ? Stream.empty() : groups.invitees(own.id()).stream().map(this::name);
            case "accept", "deny" -> self == null ? Stream.empty() : groups.invitesFor(self).stream().map(Group::name);
            case "kick", "promote", "demote", "transfer" -> own == null ? Stream.empty()
                    : own.members().keySet().stream().filter(u -> !u.equals(self)).map(this::name);
            case "color" -> NamedTextColor.NAMES.keys().stream();
            default -> Stream.empty();
        };
        return Chat.complete(args, candidates);
    }

    @Override
    public String permission() {
        return "illuniumcustom.use";
    }
}
