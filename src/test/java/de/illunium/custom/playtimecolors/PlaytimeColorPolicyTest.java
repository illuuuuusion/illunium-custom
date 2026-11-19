package de.illunium.custom.playtimecolors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class PlaytimeColorPolicyTest {

    private static final long H = 3600;
    private final PlaytimeColorPolicy policy = PlaytimeColorPolicy.DEFAULT;

    @Test
    void inclusiveThresholdsOfAllTenTiers() {
        long[] hours = {0, 10, 30, 75, 150, 300, 500, 800, 1200, 2000};
        for (int i = 0; i < hours.length; i++) {
            assertEquals(i + 1, policy.tier(hours[i] * H), "exactly " + hours[i] + " h");
            if (i > 0) assertEquals(i, policy.tier(hours[i] * H - 1), "one second before " + hours[i] + " h");
        }
        assertEquals(1, policy.tier(9 * H + 59 * 60 + 59));
        assertEquals(5, policy.tier(150 * H));
        assertEquals(10, policy.tier(1_000_000 * H));
        assertEquals(NamedTextColor.GRAY, policy.color(0));
        assertEquals(NamedTextColor.YELLOW, policy.color(2000 * H));
        assertEquals(10, new HashSet<>(policy.tiers().stream().map(PlaytimeColorPolicy.Tier::color).toList()).size(), "distinct colours");
    }

    @Test
    void packagedConfigEqualsDefaultTable() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var in = new java.io.InputStreamReader(getClass().getResourceAsStream("/config.yml"), java.nio.charset.StandardCharsets.UTF_8)) {
            yaml.load(in);
        }
        assertEquals(PlaytimeColorPolicy.DEFAULT, PlaytimeColorPolicy.parse(yaml.getMapList("playtimecolors.tiers")));
        assertSame(PlaytimeColorPolicy.DEFAULT, PlaytimeColorPolicy.parse(null));
    }

    @Test
    void rejectsWrongCountOrderStartColourAndHours() {
        assertError("exactly 10", rows(9, 0));
        assertError("exactly 10", rows(11, 0));
        assertError("start at 0", rows(10, 1));
        List<Map<?, ?>> unordered = rows(10, 0);
        ((Map<String, Object>) unordered.get(3)).put("hours", 2);
        assertError("ascending", unordered);
        List<Map<?, ?>> equal = rows(10, 0);
        ((Map<String, Object>) equal.get(3)).put("hours", 2 * 10);
        assertError("ascending", equal);
        List<Map<?, ?>> colour = rows(10, 0);
        ((Map<String, Object>) colour.get(2)).put("color", "#FF0000");
        assertError("unknown color", colour);
        List<Map<?, ?>> negative = rows(10, 0);
        ((Map<String, Object>) negative.get(0)).put("hours", -1);
        assertError("hours", negative);
        List<Map<?, ?>> text = rows(10, 0);
        ((Map<String, Object>) text.get(1)).put("hours", "10");
        assertError("hours", text);
        assertEquals(NamedTextColor.GOLD, PlaytimeColorPolicy.parse(rows(10, 0)).color(0), "colour names are case-insensitive");
    }

    private static List<Map<?, ?>> rows(int count, int firstHours) {
        List<Map<?, ?>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("hours", i == 0 ? firstHours : i * 10);
            row.put("color", "gold");
            rows.add(row);
        }
        return rows;
    }

    private static void assertError(String expected, List<Map<?, ?>> rows) {
        var error = assertThrows(IllegalArgumentException.class, () -> PlaytimeColorPolicy.parse(rows));
        assertTrue(error.getMessage().contains(expected), error.getMessage());
    }
}
