package de.illunium.custom.groups;

import de.illunium.custom.core.VisibilityService;
import de.illunium.custom.core.command.Chat;
import de.illunium.custom.core.command.UserException;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

final class GroupChatCommand implements BasicCommand, Listener {
    private final GroupService groups;
    private final Server server;
    private final VisibilityService visibility;
    private final GroupSettings settings;
    private final LongSupplier nanoTime;
    private final Map<UUID, Long> sentAt = new HashMap<>();

    GroupChatCommand(GroupService groups, Server server, VisibilityService visibility, GroupSettings settings, LongSupplier nanoTime) {
        this.groups = groups; this.server = server; this.visibility = visibility; this.settings = settings; this.nanoTime = nanoTime;
    }

    @Override public void execute(CommandSourceStack source, String[] args) {
        var sender = source.getSender();
        try {
            if (!sender.hasPermission(permission())) throw new UserException("Keine Berechtigung.");
            if (!(sender instanceof Player player)) throw new UserException("Usage: /g <Nachricht> (nur Spieler)");
            Group group = groups.groupOf(player.getUniqueId()).orElseThrow(() -> new UserException("Du bist in keiner Gruppe."));
            if (!visibility.isPubliclyVisible(player.getUniqueId())) throw new UserException("Im Vanish kannst du nicht im Gruppenchat senden. Nutze zuerst /unvanish.");
            String text = String.join(" ", args).strip();
            if (text.isBlank()) throw new UserException("Usage: /g <Nachricht>");
            if (text.codePointCount(0, text.length()) > settings.messageMaxLength())
                throw new UserException("Deine Nachricht darf höchstens " + settings.messageMaxLength() + " Zeichen enthalten.");
            long now = nanoTime.getAsLong();
            Long previous = sentAt.get(player.getUniqueId());
            if (previous != null && now - previous < settings.cooldownSeconds() * 1_000_000_000L)
                throw new UserException("Bitte warte kurz vor der nächsten Gruppennachricht.");
            Component message = Component.text("[G] ", NamedTextColor.LIGHT_PURPLE).append(group.prefix())
                    .append(Component.text(player.getName() + ": ", NamedTextColor.WHITE))
                    .append(Component.text(text, NamedTextColor.GRAY));
            for (UUID member : group.members().keySet()) {
                Player recipient = server.getPlayer(member);
                if (recipient != null) recipient.sendMessage(message);
            }
            if (settings.cooldownSeconds() > 0) sentAt.put(player.getUniqueId(), now);
        } catch (UserException e) { sender.sendMessage(Chat.error(e.getMessage())); }
    }

    @EventHandler public void quit(PlayerQuitEvent event) { sentAt.remove(event.getPlayer().getUniqueId()); }
    @Override public String permission() { return "illuniumcustom.use"; }
}
