package de.illunium.custom.groups;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GroupSettingsTest {
    @Test void defaultsAndFeatureSwitches() {
        var config = new YamlConfiguration();
        assertEquals(new GroupSettings(true, true, 256, 1), GroupSettings.load(config));
        config.set("gec.enabled", false); config.set("chat.enabled", false);
        config.set("chat.message-max-length", 1); config.set("chat.cooldown-seconds", 0);
        assertEquals(new GroupSettings(false, false, 1, 0), GroupSettings.load(config));
    }
    @Test void invalidValuesFailInsteadOfChangingTheProductRules() {
        for (var entry : Map.of("gec.min-members", 3, "gec.slots", 54, "chat.message-max-length", 257,
                "chat.cooldown-seconds", 11, "gec.enabled", "true", "chat", false).entrySet()) {
            var config = new YamlConfiguration(); config.set(entry.getKey(), entry.getValue());
            assertThrows(IllegalArgumentException.class, () -> GroupSettings.load(config), entry.getKey());
        }
        for (Object value : new Object[]{-1, 0, 4.0, "4"}) {
            var config = new YamlConfiguration(); config.set("gec.min-members", value);
            assertThrows(IllegalArgumentException.class, () -> GroupSettings.load(config));
        }
    }
}
