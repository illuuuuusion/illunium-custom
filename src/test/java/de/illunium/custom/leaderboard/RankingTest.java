package de.illunium.custom.leaderboard;

import static org.junit.jupiter.api.Assertions.*;
import de.illunium.custom.core.command.UserException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RankingTest {
    @Test void exactDefinitionsUnitsAndNoPvp() {
        assertEquals(List.of("diamonds", "placed", "mined", "playtime", "distance", "mobs", "deaths", "totems", "spawn", "fishing", "peaklevel"),
                Arrays.stream(LeaderboardDefinition.values()).map(LeaderboardDefinition::key).toList());
        assertNull(LeaderboardDefinition.find("playerkills"));
        assertEquals(LeaderboardDefinition.DIAMONDS, LeaderboardDefinition.find("DIAMONDS"));
        assertEquals("1d 2h 3m", LeaderboardDefinition.PLAYTIME.format(93780));
        assertEquals("12,34 m", LeaderboardDefinition.DISTANCE.format(1234));
        assertEquals("1,23 km", LeaderboardDefinition.DISTANCE.format(123456));
        assertEquals("42 Level", LeaderboardDefinition.PEAKLEVEL.format(42));
        for (var definition : LeaderboardDefinition.values()) assertFalse(definition.history.isBlank());
    }
    @Test void competitionRanksDeterministicTiesPositiveValuesAndCurrentOpFilter() {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), op = UUID.randomUUID();
        var snapshot = new RankingSnapshot(List.of(new RankingSnapshot.Value(b, "Alex", 50), new RankingSnapshot.Value(a, "Alex", 50),
                new RankingSnapshot.Value(op, "Admin", 100), new RankingSnapshot.Value(UUID.randomUUID(), "Zero", 0),
                new RankingSnapshot.Value(UUID.randomUUID(), "Last", 2)));
        var rows = snapshot.excluding(Set.of(op));
        assertEquals(List.of(1, 1, 3), rows.stream().map(RankingSnapshot.Row::rank).toList());
        assertEquals(List.of(a, b), rows.subList(0, 2).stream().map(RankingSnapshot.Row::player).toList());
        assertEquals(4, snapshot.excluding(Set.of()).size(), "OP filtering uses the current list, including offline OPs");
        assertThrows(UnsupportedOperationException.class, () -> rows.clear());
    }
    @Test void pagingAndOwnRankBeyondTopTen() {
        var values = new ArrayList<RankingSnapshot.Value>();
        for (int i = 0; i < 25; i++) values.add(new RankingSnapshot.Value(UUID.randomUUID(), "Player" + i, 100 - i));
        var rows = new RankingSnapshot(values).excluding(Set.of());
        assertEquals(10, rows.stream().skip(10).limit(10).count());
        assertEquals(25, rows.getLast().rank());
        assertEquals(2, LeaderboardModule.page("2", 3));
        for (String invalid : List.of("0", "-1", "4", "bad", "9999999999999999"))
            assertThrows(UserException.class, () -> LeaderboardModule.page(invalid, 3));
    }
}
