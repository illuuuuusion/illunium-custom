package de.illunium.custom.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PluginConfigTest {

    private static final List<String> KNOWN = List.of("recipes", "sit");

    @Test
    void emptyConfigUsesDefaults() throws Exception {
        PluginConfig config = PluginConfig.load(yaml(""), KNOWN);
        assertTrue(config.moduleEnabled("recipes"));
        assertEquals("data.db", config.storageFile());
    }

    @Test
    void readsModuleFlags() throws Exception {
        PluginConfig config = PluginConfig.load(yaml("modules:\n  recipes: false\n  sit: true\n"), KNOWN);
        assertFalse(config.moduleEnabled("recipes"));
        assertTrue(config.moduleEnabled("sit"));
    }

    @Test
    void rejectsUnknownModuleNonBooleanAndPathFileNameTogether() throws Exception {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> PluginConfig.load(
                yaml("modules:\n  recipe: true\n  sit: maybe\nstorage:\n  filename: ../other.db\n"), KNOWN));
        assertTrue(error.getMessage().contains("modules.recipe: unknown module"));
        assertTrue(error.getMessage().contains("modules.sit: must be true or false"));
        assertTrue(error.getMessage().contains("storage.filename"));
    }

    @Test
    void rejectsModulesThatIsNotASection() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> PluginConfig.load(yaml("modules: true\n"), KNOWN));
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }
}
