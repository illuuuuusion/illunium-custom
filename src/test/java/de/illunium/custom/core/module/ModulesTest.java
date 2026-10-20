package de.illunium.custom.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.illunium.custom.core.config.PluginConfig;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ModulesTest {

    @Test
    void idsAreUniqueLowercaseAndListedInPackagedConfig() throws Exception {
        List<String> ids = Modules.create().stream().map(FeatureModule::id).toList();
        assertEquals(ids.size(), new HashSet<>(ids).size(), "duplicate module id");
        ids.forEach(id -> assertTrue(id.matches("[a-z]+"), id));

        YamlConfiguration yaml = new YamlConfiguration();
        try (var reader = new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        PluginConfig.load(yaml, ids);
        assertEquals(new HashSet<>(ids), yaml.getConfigurationSection("modules").getKeys(false),
                "config.yml must list every registered module");
    }
}
