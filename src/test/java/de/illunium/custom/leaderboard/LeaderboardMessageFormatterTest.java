package de.illunium.custom.leaderboard;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

class LeaderboardMessageFormatterTest {
    @Test void allElevenCategoriesKeepTheirCommandAndUnavailableHint() {
        assertEquals(11, LeaderboardDefinition.values().length);
        for (var definition : LeaderboardDefinition.values()) {
            var line = LeaderboardMessageFormatter.overviewEntry(definition, true);
            assertEquals(ClickEvent.runCommand("/leaderboard " + definition.key()), line.clickEvent());
            assertTrue(plain(line).contains(definition.title)); assertNotNull(line.color());
            assertTrue(plain(LeaderboardMessageFormatter.header(definition)).contains(definition.title));
        }
        assertTrue(plain(LeaderboardMessageFormatter.overviewEntry(LeaderboardDefinition.PLAYTIME, false)).contains("nicht verfügbar"));
    }
    @Test void medalsNumbersLiteralNamesAndOwnRankArePreserved() {
        var colors = List.of(0xFFD54F, 0xBFC7D5, 0xCD9B6D, 0xAEB7C2);
        for (int rank = 1; rank <= 4; rank++) {
            var row = new RankingSnapshot.Row(UUID.randomUUID(), "<red>Alex", 482, rank);
            var line = LeaderboardMessageFormatter.entry(LeaderboardDefinition.DIAMONDS, row);
            assertEquals(TextColor.color(colors.get(rank - 1)), line.color());
            assertEquals("#" + rank + "  <red>Alex — 482 Erze", plain(line));
            assertEquals("Dein Platz: #" + rank + " — 482 Erze", plain(LeaderboardMessageFormatter.ownRank(LeaderboardDefinition.DIAMONDS, row)));
        }
    }
    @Test void pageControlsHaveCorrectTargetsAndNoInvalidNeighbors() {
        var middle = LeaderboardMessageFormatter.navigation(LeaderboardDefinition.MINED, 2, 3);
        assertEquals(List.of(ClickEvent.runCommand("/leaderboard mined 1"), ClickEvent.runCommand("/leaderboard mined 3")),
                middle.children().stream().map(Component::clickEvent).toList());
        assertTrue(LeaderboardMessageFormatter.navigation(LeaderboardDefinition.MINED, 1, 1).children().isEmpty());
        assertEquals(1, LeaderboardMessageFormatter.navigation(LeaderboardDefinition.MINED, 1, 3).children().size());
        assertEquals(1, LeaderboardMessageFormatter.navigation(LeaderboardDefinition.MINED, 3, 3).children().size());
    }
    private static String plain(Component component) {
        return (component instanceof TextComponent text ? text.content() : "")
                + component.children().stream().map(LeaderboardMessageFormatterTest::plain).collect(java.util.stream.Collectors.joining());
    }
}
