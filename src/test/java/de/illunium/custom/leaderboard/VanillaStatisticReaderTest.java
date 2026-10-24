package de.illunium.custom.leaderboard;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.junit.jupiter.api.Test;

class VanillaStatisticReaderTest {
    @Test void sumsBlocksOnceAndOnlyWalkingStatisticsAndRejectsUnsupportedMaterialsOnce() {
        AtomicInteger invalid = new AtomicInteger();
        var reader = new VanillaStatisticReader(List.of(Material.STONE, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.STICK));
        OfflinePlayer player = (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(), new Class<?>[]{OfflinePlayer.class}, (p, m, args) -> {
            assertEquals("getStatistic", m.getName());
            Statistic stat = (Statistic) args[0];
            if (args.length == 2) return switch ((Material) args[1]) {
                case STICK -> { invalid.incrementAndGet(); throw new IllegalArgumentException("not a block"); }
                case STONE -> 7; case DIAMOND_ORE -> 2; case DEEPSLATE_DIAMOND_ORE -> 3;
                default -> throw new AssertionError(args[1]);
            };
            return switch (stat) {
                case WALK_ONE_CM -> Integer.MAX_VALUE; case SPRINT_ONE_CM, CROUCH_ONE_CM -> 100;
                case MOB_KILLS -> 4; case DEATHS -> 5; case FISH_CAUGHT -> 6;
                default -> throw new AssertionError("Unexpected data source " + stat);
            };
        });
        for (int i = 0; i < 2; i++) {
            var cursor = reader.cursor(player);
            int steps = 0; while (!cursor.step()) assertTrue(++steps < 10);
            assertEquals(Map.of(LeaderboardDefinition.MINED, 12L, LeaderboardDefinition.DIAMONDS, 5L,
                    LeaderboardDefinition.DISTANCE, (long) Integer.MAX_VALUE + 200,
                    LeaderboardDefinition.MOBS, 4L, LeaderboardDefinition.DEATHS, 5L, LeaderboardDefinition.FISHING, 6L), cursor.values());
        }
        assertEquals(1, invalid.get());
    }
}
