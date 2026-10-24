package de.illunium.custom.leaderboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

/** Main-thread cursor: at most 32 block-stat reads per step, with a 2 ms cooperative budget. */
final class VanillaStatisticReader {
    private final List<Material> materials;

    VanillaStatisticReader() {
        this(Arrays.stream(Material.values()).filter(m -> !m.isLegacy() && m.isBlock() && !m.isAir()).toList());
    }
    VanillaStatisticReader(List<Material> materials) { this.materials = new ArrayList<>(materials); }

    Cursor cursor(OfflinePlayer player) { return new Cursor(player); }

    final class Cursor {
        private final OfflinePlayer player;
        private final Iterator<Material> blocks = materials.iterator();
        private long mined;
        private final Map<LeaderboardDefinition, Long> values = new EnumMap<>(LeaderboardDefinition.class);
        Cursor(OfflinePlayer player) { this.player = player; }

        boolean step() {
            long until = System.nanoTime() + 2_000_000;
            int count = 0;
            while (blocks.hasNext() && count++ < 32) {
                Material material = blocks.next();
                try { mined += player.getStatistic(Statistic.MINE_BLOCK, material); }
                catch (IllegalArgumentException ignored) { blocks.remove(); }
                if (System.nanoTime() >= until) return false;
            }
            if (blocks.hasNext()) return false;
            values.put(LeaderboardDefinition.MINED, mined);
            values.put(LeaderboardDefinition.DIAMONDS, (long) player.getStatistic(Statistic.MINE_BLOCK, Material.DIAMOND_ORE)
                    + player.getStatistic(Statistic.MINE_BLOCK, Material.DEEPSLATE_DIAMOND_ORE));
            values.put(LeaderboardDefinition.DISTANCE, (long) player.getStatistic(Statistic.WALK_ONE_CM)
                    + player.getStatistic(Statistic.SPRINT_ONE_CM) + player.getStatistic(Statistic.CROUCH_ONE_CM));
            values.put(LeaderboardDefinition.MOBS, (long) player.getStatistic(Statistic.MOB_KILLS));
            values.put(LeaderboardDefinition.DEATHS, (long) player.getStatistic(Statistic.DEATHS));
            values.put(LeaderboardDefinition.FISHING, (long) player.getStatistic(Statistic.FISH_CAUGHT));
            return true;
        }
        Map<LeaderboardDefinition, Long> values() { return Map.copyOf(values); }
    }
}
