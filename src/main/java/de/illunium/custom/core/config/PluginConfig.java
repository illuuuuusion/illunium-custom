package de.illunium.custom.core.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/** Validated core settings from {@code config.yml}. Module-specific sections are validated by each module. */
public record PluginConfig(Map<String, Boolean> modules, String storageFile) {

    private static final Pattern FILE_NAME = Pattern.compile("^[A-Za-z0-9_-][A-Za-z0-9._-]*$");

    /** Modules missing from {@code modules:} are enabled. */
    public boolean moduleEnabled(String id) {
        return modules.getOrDefault(id, true);
    }

    public static PluginConfig load(ConfigurationSection root, Collection<String> knownModules) {
        List<String> errors = new ArrayList<>();
        Map<String, Boolean> modules = new HashMap<>();

        ConfigurationSection moduleSection = root.getConfigurationSection("modules");
        if (moduleSection == null && root.contains("modules")) {
            errors.add("'modules' must be a section of module: true|false");
        } else if (moduleSection != null) {
            for (String key : moduleSection.getKeys(false)) {
                if (!knownModules.contains(key)) {
                    errors.add("modules." + key + ": unknown module (known: " + knownModules + ")");
                } else if (!moduleSection.isBoolean(key)) {
                    errors.add("modules." + key + ": must be true or false");
                } else {
                    modules.put(key, moduleSection.getBoolean(key));
                }
            }
        }

        String storageFile = root.getString("storage.filename", "data.db");
        if (!FILE_NAME.matcher(storageFile).matches()) {
            errors.add("storage.filename: must be a plain file name inside the plugin folder, got '" + storageFile + "'");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid config.yml: " + String.join("; ", errors));
        }
        return new PluginConfig(Map.copyOf(modules), storageFile);
    }
}
