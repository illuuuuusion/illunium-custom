package de.illunium.custom.groups;

import de.illunium.custom.core.command.Chat;
import de.illunium.custom.core.command.UserException;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

final class GroupChestCommand implements BasicCommand {
    private final GroupChestService chests;
    GroupChestCommand(GroupChestService chests) { this.chests = chests; }

    @Override public void execute(CommandSourceStack source, String[] args) {
        var sender = source.getSender();
        if (!sender.hasPermission(permission())) { sender.sendMessage(Chat.error("Keine Berechtigung.")); return; }
        if (!(sender instanceof Player player) || args.length != 0) {
            sender.sendMessage(Chat.error("Usage: /gec (nur Spieler)")); return;
        }
        try { chests.open(player); }
        catch (UserException e) { player.sendMessage(Chat.error(e.getMessage())); }
    }
    @Override public String permission() { return "illuniumcustom.use"; }
}
