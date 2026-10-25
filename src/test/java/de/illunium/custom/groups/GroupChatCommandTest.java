package de.illunium.custom.groups;

import static org.junit.jupiter.api.Assertions.*;
import de.illunium.custom.TestMainThread;
import de.illunium.custom.core.VisibilityService;
import de.illunium.custom.storage.Database;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroupChatCommandTest {
    @TempDir Path dir;
    private final Map<UUID, Player> online = new HashMap<>();
    private final Map<UUID, List<Component>> received = new HashMap<>();
    private final TestMainThread main = new TestMainThread();

    @Test void privateCurrentMembershipOfflineAndHiddenRecipientsAndLiteralText() throws Exception {
        try (var db = Database.open(dir.resolve("data.db"))) {
            var groups = GroupService.load(db, main, main.clock(), (p,g) -> {});
            Player a = player("Alex"), b = player("Bob"), outsider = player("Other"), offline = player("Offline");
            main.await(groups.create(a.getUniqueId(), "Nova", "NOVA"));
            main.await(groups.create(outsider.getUniqueId(), "Other", "OT"));
            join(groups, a, b); join(groups, a, offline); online.remove(offline.getUniqueId());
            main.await(groups.recolor(a.getUniqueId(), "#123456"));
            Server server = server(); var visibility = new VisibilityService(server);
            visibility.setHidden(b.getUniqueId(), true);
            var command = new GroupChatCommand(groups, server, visibility, new GroupSettings(true, true, 256, 0), () -> 0);
            command.execute(source(a), new String[]{"<red>Hallo", "", "§c&b"});
            assertEquals(1, messages(a).size()); assertEquals(messages(a), messages(b));
            assertTrue(messages(outsider).isEmpty()); assertTrue(messages(offline).isEmpty());
            Component message = messages(a).getFirst();
            assertEquals("[G] [NOVA] Alex: <red>Hallo  §c&b", plain(message));
            assertEquals(TextColor.color(0x123456), message.children().getFirst().color());
            assertNull(message.children().getLast().clickEvent());
            main.await(groups.kick(a.getUniqueId(), b.getUniqueId()));
            command.execute(source(a), new String[]{"After kick"});
            assertEquals(1, messages(b).size());
            join(groups, a, b);
            command.execute(source(a), new String[]{"After join"}); assertEquals(2, messages(b).size());
            main.await(groups.leave(b.getUniqueId()));
            command.execute(source(a), new String[]{"After leave"}); assertEquals(2, messages(b).size());
        }
    }

    @Test void invalidSendersVanishWhitespaceCodepointsAndPerSenderCooldown() throws Exception {
        try (var db = Database.open(dir.resolve("data.db"))) {
            var groups = GroupService.load(db, main, main.clock(), (p,g) -> {});
            Player a = player("Alex"), b = player("Bob"), outsider = player("Other");
            main.await(groups.create(a.getUniqueId(), "Nova", "NOVA")); join(groups, a, b);
            Server server = server(); var visibility = new VisibilityService(server); var now = new AtomicLong();
            var command = new GroupChatCommand(groups, server, visibility, new GroupSettings(true, true, 256, 1), now::get);
            command.execute(source(outsider), new String[]{"No group"});
            assertTrue(plain(messages(outsider).getLast()).contains("keiner Gruppe"));
            List<Component> consoleMessages = new ArrayList<>();
            CommandSender console = (CommandSender) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CommandSender.class}, (p,m,args) -> switch (m.getName()) {
                case "hasPermission" -> true;
                case "sendMessage" -> { consoleMessages.add((Component)args[0]); yield null; }
                default -> throw new UnsupportedOperationException(m.getName());
            });
            command.execute(source(console), new String[]{"No console"}); assertTrue(plain(consoleMessages.getFirst()).contains("nur Spieler"));
            visibility.setHidden(a.getUniqueId(), true);
            command.execute(source(a), new String[]{"hidden"}); assertTrue(plain(messages(a).getLast()).contains("/unvanish"));
            assertTrue(messages(b).isEmpty()); visibility.setHidden(a.getUniqueId(), false);
            for (String text : new String[]{"", "   ", "x".repeat(257)}) command.execute(source(a), new String[]{text});
            assertTrue(messages(b).isEmpty());
            command.execute(source(a), new String[]{"😀".repeat(256)}); assertEquals(1, messages(b).size());
            command.execute(source(a), new String[]{"too soon"}); assertTrue(plain(messages(a).getLast()).contains("warte"));
            command.execute(source(b), new String[]{"own cooldown"}); assertEquals(2, messages(b).size());
            now.set(1_000_000_000L); command.execute(source(a), new String[]{"exactly one second"}); assertEquals(3, messages(b).size());
        }
    }

    private void join(GroupService groups, Player leader, Player member) {
        main.await(groups.invite(leader.getUniqueId(), member.getUniqueId())); main.await(groups.accept(member.getUniqueId(), "Nova"));
    }
    private List<Component> messages(Player player) { return received.get(player.getUniqueId()); }
    static String plain(Component component) {
        return (component instanceof TextComponent text ? text.content() : "") + component.children().stream().map(GroupChatCommandTest::plain).collect(java.util.stream.Collectors.joining());
    }
    private Player player(String name) {
        UUID id = UUID.randomUUID(); received.put(id, new ArrayList<>());
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class}, (p,m,args) -> switch (m.getName()) {
            case "getUniqueId" -> id; case "getName" -> name; case "hasPermission" -> true;
            case "sendMessage" -> { received.get(id).add((Component)args[0]); yield null; }
            default -> throw new UnsupportedOperationException(m.getName());
        });
        online.put(id, player); return player;
    }
    private Server server() {
        return (Server) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Server.class}, (p,m,args) -> {
            if (m.getName().equals("getPlayer")) return online.get(args[0]);
            throw new UnsupportedOperationException(m.getName());
        });
    }
    private CommandSourceStack source(CommandSender sender) {
        return (CommandSourceStack) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CommandSourceStack.class}, (p,m,args) -> {
            if (m.getName().equals("getSender")) return sender;
            throw new UnsupportedOperationException(m.getName());
        });
    }
}
