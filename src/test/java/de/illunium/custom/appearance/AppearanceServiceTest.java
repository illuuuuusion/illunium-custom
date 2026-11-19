package de.illunium.custom.appearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;

/** Single-writer composition of group tag, playtime name colour and marriage heart (Patch v1.5 §3.2). */
class AppearanceServiceTest {

    private static final class FakeTeam {
        final String name;
        Component prefix;
        NamedTextColor color;
        final Set<String> entries = new HashSet<>();
        FakeTeam(String name) { this.name = name; }
    }

    private final Map<String, FakeTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, Player> online = new HashMap<>();
    private final Map<UUID, Component> listNames = new HashMap<>();
    private final AppearanceService appearance = new AppearanceService(server());

    @Test
    void colourAloneDecoratesAndKeepsHexPrefixAndHeartSeparate() {
        Player alex = player("Alex");
        appearance.setNameColor(alex.getUniqueId(), NamedTextColor.AQUA);
        FakeTeam team = team(alex);
        assertEquals(NamedTextColor.AQUA, team.color);
        assertNull(team.prefix, "no group: no tag");
        assertTrue(team.entries.contains("Alex"));
        assertEquals("Alex", plain(listNames.get(alex.getUniqueId())));
        assertEquals(NamedTextColor.AQUA, listNames.get(alex.getUniqueId()).children().getFirst().color());

        Component tag = Component.text("[NOVA] ", TextColor.color(0x123456));
        Component heart = Component.text(" ❤", NamedTextColor.RED);
        appearance.setPrefix(alex.getUniqueId(), tag);
        appearance.setSuffix(alex.getUniqueId(), heart);
        Component list = listNames.get(alex.getUniqueId());
        assertEquals("[NOVA] Alex ❤", plain(list), "tag exactly once");
        assertEquals(TextColor.color(0x123456), list.children().get(0).color(), "group colour untouched");
        assertEquals(NamedTextColor.AQUA, list.children().get(1).color());
        assertEquals(NamedTextColor.RED, list.children().get(2).color());
        assertEquals(tag, team.prefix);
        assertEquals(NamedTextColor.AQUA, team.color);

        appearance.setNameColor(alex.getUniqueId(), NamedTextColor.GOLD);
        assertEquals(NamedTextColor.GOLD, team(alex).color);
        assertEquals(NamedTextColor.GOLD, listNames.get(alex.getUniqueId()).children().get(1).color());
    }

    @Test
    void removingTheLastDecorationDropsTeamAndListName() {
        Player alex = player("Alex");
        appearance.setPrefix(alex.getUniqueId(), Component.text("[A] "));
        appearance.setNameColor(alex.getUniqueId(), NamedTextColor.GRAY);
        appearance.setPrefix(alex.getUniqueId(), null);
        assertNull(team(alex).prefix);
        assertEquals(NamedTextColor.GRAY, team(alex).color, "colour keeps the team without a group");
        appearance.setNameColor(alex.getUniqueId(), null);
        assertTrue(teams.isEmpty());
        assertNull(listNames.get(alex.getUniqueId()));
    }

    @Test
    void offlinePlayersAreIgnoredAndQuitAndCloseCleanUp() {
        appearance.setNameColor(UUID.randomUUID(), NamedTextColor.RED);
        assertTrue(teams.isEmpty());
        Player alex = player("Alex"), bob = player("Bob");
        appearance.setNameColor(alex.getUniqueId(), NamedTextColor.RED);
        appearance.setNameColor(bob.getUniqueId(), NamedTextColor.BLUE);
        appearance.onQuit(new PlayerQuitEvent(alex, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        online.remove(alex.getUniqueId());
        assertEquals(1, teams.size());
        online.put(alex.getUniqueId(), alex);
        appearance.setPrefix(alex.getUniqueId(), Component.text("[A] "));
        assertNull(team(alex).color, "colour was forgotten on quit; the module reports again on join");
        appearance.close();
        assertTrue(teams.isEmpty());
        assertNull(listNames.get(bob.getUniqueId()));
        assertNull(listNames.get(alex.getUniqueId()));
    }

    private FakeTeam team(Player player) {
        return teams.get(AppearanceService.TEAM_PREFIX + player.getUniqueId().toString().replace("-", ""));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private Player player(String name) {
        UUID id = UUID.randomUUID();
        Player player = proxy(Player.class, (m, args) -> switch (m) {
            case "getUniqueId" -> id;
            case "getName" -> name;
            case "playerListName" -> { listNames.put(id, (Component) args[0]); yield null; }
            default -> throw new UnsupportedOperationException(m);
        });
        online.put(id, player);
        return player;
    }

    private Server server() {
        Scoreboard board = proxy(Scoreboard.class, (m, args) -> switch (m) {
            case "getTeam" -> teams.containsKey((String) args[0]) ? team(teams.get((String) args[0])) : null;
            case "registerNewTeam" -> { FakeTeam t = new FakeTeam((String) args[0]); teams.put(t.name, t); yield team(t); }
            case "getTeams" -> Set.copyOf(teams.values().stream().map(this::team).toList());
            default -> throw new UnsupportedOperationException(m);
        });
        ScoreboardManager manager = proxy(ScoreboardManager.class, (m, args) -> {
            if (m.equals("getMainScoreboard")) return board;
            throw new UnsupportedOperationException(m);
        });
        return proxy(Server.class, (m, args) -> switch (m) {
            case "getScoreboardManager" -> manager;
            case "getPlayer" -> online.get((UUID) args[0]);
            default -> throw new UnsupportedOperationException(m);
        });
    }

    private Team team(FakeTeam t) {
        return proxy(Team.class, (m, args) -> switch (m) {
            case "getName" -> t.name;
            case "prefix" -> { t.prefix = (Component) args[0]; yield null; }
            case "color" -> { t.color = (NamedTextColor) args[0]; yield null; }
            case "hasEntry" -> t.entries.contains((String) args[0]);
            case "addEntry" -> { t.entries.add((String) args[0]); yield null; }
            case "unregister" -> { teams.remove(t.name); yield null; }
            case "hashCode" -> t.hashCode();
            case "equals" -> args[0] == t;
            default -> throw new UnsupportedOperationException(m);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(AppearanceServiceTest.class.getClassLoader(), new Class<?>[]{type},
                (p, method, args) -> handler.apply(method.getName(), args));
    }
}
