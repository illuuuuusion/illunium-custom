package de.illunium.custom.beacons;

import de.illunium.custom.core.command.Chat;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.core.module.ModuleContext;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/** {@code /beacon info} for the beacon directly below the player's feet, and {@code /ic beacon setowner}. */
final class BeaconCommand implements BasicCommand {

    private static final String[] LEVELS = {"I", "II"};

    private final BeaconModule beacons;
    private final ModuleContext context;

    BeaconCommand(BeaconModule beacons, ModuleContext context) {
        this.beacons = beacons;
        this.context = context;
    }

    /** Same X/Z, one block below the feet; no radius, no look target. */
    static Block beaconBelow(CommandSender sender) {
        if (!(sender instanceof Player player)) throw new UserException("Nur Spieler, die direkt auf einem Beacon stehen.");
        Block below = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        if (below.getType() != Material.BEACON) throw new UserException("Stell dich direkt auf den Beacon (Block unter deinen Füßen).");
        return below;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        try {
            if (!sender.hasPermission(permission())) throw new UserException("Keine Berechtigung.");
            if (args.length > 1 || args.length == 1 && !args[0].equalsIgnoreCase("info")) throw new UserException("Usage: /beacon info");
            Block block = beaconBelow(sender);
            BeaconModule.Status status = beacons.status(block);
            String owner = beacons.owner(block).map(id -> context.identities().name(id).orElse(id.toString()))
                    .orElse("keiner (vor v1.5 oder ohne Spieler platziert)");
            sender.sendMessage(Chat.info("Beacon " + block.getX() + " " + block.getY() + " " + block.getZ() + " · Eigentümer: " + owner));
            sender.sendMessage(Chat.info("Pyramide: Stufe " + status.tier() + " · Himmel: " + (status.covered() ? "überdacht" : "frei")
                    + " · Auswahl: " + name(status.primary()) + " / " + name(status.secondary())));
            sender.sendMessage(Chat.info("Wirkung: " + effect(status)));
            sender.sendMessage(Chat.info("Strahl: Vanilla. Ausblenden bei freiem Himmel ist ohne Client-Mod nicht möglich."));
        } catch (UserException e) {
            sender.sendMessage(Chat.error(e.getMessage()));
        }
    }

    private String effect(BeaconModule.Status status) {
        if (status.tier() == 0) return "inaktiv – keine vollständige Pyramide";
        if (status.primary() == null) return "inaktiv – kein Effekt gewählt";
        if (status.covered() && !beacons.coveredEffects()) return "inaktiv – überdacht";
        return status.grants().stream().map(g -> name(g.type()) + " " + LEVELS[g.amplifier()]).collect(Collectors.joining(", "))
                + " im Umkreis von " + (int) BeaconRules.range(status.tier()) + " Blöcken"
                + (status.covered() ? " (überdacht, vom Plugin)" : " (Vanilla)");
    }

    private static String name(PotionEffectType type) {
        return type == null ? "–" : type.getKey().getKey();
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return args.length <= 1 ? Chat.complete(args, java.util.stream.Stream.of("info")) : List.of();
    }

    @Override
    public String permission() {
        return "illuniumcustom.use";
    }

    /** {@code /ic beacon setowner <player>}: recovery for beacons placed before v1.5; audited in the server log. */
    BasicCommand admin() {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                CommandSender sender = source.getSender();
                try {
                    if (args.length != 2 || !args[0].equalsIgnoreCase("setowner")) throw new UserException("Usage: /ic beacon setowner <spieler>");
                    Block block = beaconBelow(sender);
                    UUID owner = resolve(args[1]);
                    String previous = beacons.owner(block).map(UUID::toString).orElse("none");
                    beacons.setOwner(block, owner);
                    context.plugin().getLogger().info("[admin] " + sender.getName() + " set beacon owner at " + block.getWorld().getName()
                            + " " + block.getX() + " " + block.getY() + " " + block.getZ() + ": " + previous + " -> " + owner + " (" + args[1] + ")");
                    sender.sendMessage(Chat.ok("Eigentümer dieses Beacons ist jetzt " + args[1] + "."));
                } catch (UserException e) {
                    sender.sendMessage(Chat.error(e.getMessage()));
                }
            }

            @Override
            public Collection<String> suggest(CommandSourceStack source, String[] args) {
                return args.length <= 1 ? Chat.complete(args, java.util.stream.Stream.of("setowner")) : List.of();
            }
        };
    }

    private UUID resolve(String name) {
        Player online = context.plugin().getServer().getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        List<UUID> known = context.identities().findByName(name);
        if (known.size() != 1)
            throw new UserException(known.isEmpty() ? "Unbekannter Spieler " + name + "." : "Mehrere Spieler hießen zuletzt " + name + ".");
        return known.getFirst();
    }
}
