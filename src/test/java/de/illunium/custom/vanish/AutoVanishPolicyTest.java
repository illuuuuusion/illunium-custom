package de.illunium.custom.vanish;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class AutoVanishPolicyTest {
    private final UUID player = UUID.randomUUID();
    @Test void compatibilityAndEveryPolicyCombination() {
        assertTrue(AutoVanishPolicy.load(new YamlConfiguration()).hidden(true, player));
        for (boolean op : new boolean[]{true, false}) for (boolean fallback : new boolean[]{true, false}) {
            assertEquals(op && fallback, new AutoVanishPolicy(fallback, Map.of()).hidden(op, player));
            for (boolean override : new boolean[]{true, false}) {
                assertEquals(op && override, new AutoVanishPolicy(fallback, Map.of(player, override)).hidden(op, player));
            }
        }
    }
    @Test void loadsBooleansAndRejectsDangerousCoercionAndMalformedUuid() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("auto-vanish-default: false\nauto-vanish-overrides:\n  '" + player + "': true\n");
        assertTrue(AutoVanishPolicy.load(config).hidden(true, player));
        assertFalse(AutoVanishPolicy.load(config).hidden(true, UUID.randomUUID()));
        for (String yaml : new String[]{"auto-vanish-default: 'false'", "auto-vanish-overrides: []",
                "auto-vanish-overrides:\n  bad: false", "auto-vanish-overrides:\n  '" + player + "': 'false'",
                "auto-vanish-overrides:\n  '1-1-1-1-1': false"}) {
            config.loadFromString(yaml);
            assertThrows(IllegalArgumentException.class, () -> AutoVanishPolicy.load(config), yaml);
        }
    }
}
