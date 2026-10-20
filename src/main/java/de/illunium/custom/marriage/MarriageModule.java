package de.illunium.custom.marriage;

import static de.illunium.custom.core.command.Chat.arg;
import static de.illunium.custom.core.command.Chat.error;
import static de.illunium.custom.core.command.Chat.info;
import static de.illunium.custom.core.command.Chat.ok;

import de.illunium.custom.core.command.Chat;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.InstantSource;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** {@code /marry} and {@code /divorce}; reports the tablist heart to the appearance service (MVP §5). */
public final class MarriageModule implements FeatureModule, Listener {

    private ModuleContext context;
    private Server server;
    private MarriageService marriages;
    private Component onlineHeart;
    private Component offlineHeart;

    @Override
    public String id() {
        return "marriage";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        this.server = context.plugin().getServer();
        String offlineSymbol = context.settings().getString("offline-symbol", "💔");
        if (offlineSymbol.isBlank()) {
            throw new IllegalArgumentException("marriage.offline-symbol must not be empty");
        }
        onlineHeart = Component.text(" ❤", NamedTextColor.RED);
        offlineHeart = Component.text(" " + offlineSymbol, NamedTextColor.GRAY);

        // Loading reports every married player once, which decorates players already online.
        marriages = MarriageService.load(context.database(), context.mainThread(), InstantSource.system(),
                (player, partner) -> context.appearance().setSuffix(player, partner == null ? null : heart(server.getPlayer(partner) != null)));
        server.getPluginManager().registerEvents(this, context.plugin());
        context.plugin().registerCommand("marry", "Propose, accept or check a marriage", List.of(), new MarryCommand());
        context.plugin().registerCommand("divorce", "End your marriage", List.of(), new DivorceCommand());
    }

    @Override
    public void disable() {
        // Marriages are committed per command; proposals and divorce confirmations are dropped on purpose.
    }

    private Component heart(boolean partnerOnline) {
        return partnerOnline ? onlineHeart : offlineHeart;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        marriages.partner(uuid).ifPresent(partner -> {
            context.appearance().setSuffix(uuid, heart(server.getPlayer(partner) != null));
            context.appearance().setSuffix(partner, heart(true));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // The quitting player still counts as online during this event, so pass the new state explicitly.
        marriages.partner(event.getPlayer().getUniqueId()).ifPresent(partner -> context.appearance().setSuffix(partner, heart(false)));
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

    private final class MarryCommand implements BasicCommand {

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            try {
                String first = arg(args, 0, "/marry <player> | accept | deny | info [player]");
                switch (first.toLowerCase(Locale.ROOT)) {
                    case "info" -> showInfo(sender, args);
                    case "accept" -> {
                        Player player = player(sender);
                        context.reply(marriages.accept(player.getUniqueId()), player, partner -> {
                            message(partner, ok(player.getName() + " accepted your proposal. You are now married!"));
                            player.sendMessage(ok("You are now married to " + name(partner) + "!"));
                        });
                    }
                    case "deny" -> {
                        Player player = player(sender);
                        UUID proposer = marriages.deny(player.getUniqueId());
                        message(proposer, info(player.getName() + " declined your proposal."));
                        player.sendMessage(ok("Proposal from " + name(proposer) + " declined."));
                    }
                    default -> {
                        Player player = player(sender);
                        Player target = server.getPlayerExact(first);
                        if (target == null) {
                            throw new UserException("That player is not online.");
                        }
                        marriages.propose(player.getUniqueId(), target.getUniqueId());
                        target.sendMessage(info(player.getName() + " wants to marry you. ")
                                .append(Component.text("[Accept]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/marry accept")))
                                .append(Component.text(" "))
                                .append(Component.text("[Deny]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/marry deny")))
                                .append(info(" (expires in 5 minutes)")));
                        player.sendMessage(ok("Proposal sent to " + target.getName() + "."));
                    }
                }
            } catch (UserException e) {
                sender.sendMessage(error(e.getMessage()));
            }
        }

        private void showInfo(CommandSender sender, String[] args) {
            UUID target;
            if (args.length > 1) {
                Player online = server.getPlayerExact(args[1]);
                List<UUID> known = online != null ? List.of(online.getUniqueId()) : context.identities().findByName(args[1]);
                if (known.size() != 1) {
                    throw new UserException(known.isEmpty() ? "Unknown player " + args[1] + "." : "Several players were last seen as " + args[1] + ".");
                }
                target = known.getFirst();
            } else {
                target = player(sender).getUniqueId();
            }
            sender.sendMessage(marriages.partner(target)
                    .map(partner -> info(name(target) + " is married to " + name(partner)
                            + (server.getPlayer(partner) != null ? " (online)." : " (offline).")))
                    .orElseGet(() -> info(name(target) + " is not married.")));
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            if (args.length <= 1) {
                return Chat.complete(args, Stream.concat(Stream.of("accept", "deny", "info"),
                        server.getOnlinePlayers().stream().filter(p -> p != source.getSender()).map(Player::getName)));
            }
            return args.length == 2 && args[0].equalsIgnoreCase("info")
                    ? Chat.complete(args, server.getOnlinePlayers().stream().map(Player::getName))
                    : List.of();
        }

        @Override
        public String permission() {
            return "illuniumcustom.use";
        }
    }

    private final class DivorceCommand implements BasicCommand {

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            try {
                Player player = player(sender);
                UUID actor = player.getUniqueId();
                switch (args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT)) {
                    case "" -> player.sendMessage(info("Divorce " + name(marriages.requestDivorce(actor))
                            + "? Type /divorce confirm within 30 seconds or /divorce cancel."));
                    case "confirm" -> context.reply(marriages.confirmDivorce(actor), player, former -> {
                        message(former, info(player.getName() + " divorced you."));
                        player.sendMessage(ok("You are now divorced from " + name(former) + "."));
                    });
                    case "cancel" -> {
                        marriages.cancelDivorce(actor);
                        player.sendMessage(ok("Divorce cancelled."));
                    }
                    default -> throw new UserException("Usage: /divorce [confirm|cancel]");
                }
            } catch (UserException e) {
                sender.sendMessage(error(e.getMessage()));
            }
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            return args.length <= 1 ? Chat.complete(args, Stream.of("confirm", "cancel")) : List.of();
        }

        @Override
        public String permission() {
            return "illuniumcustom.use";
        }
    }

    private static Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        throw new UserException("Only players can do that.");
    }
}
