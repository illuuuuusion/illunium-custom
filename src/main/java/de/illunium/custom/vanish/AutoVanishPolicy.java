package de.illunium.custom.vanish;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/** Immutable join defaults; never grants operator privileges. */
record AutoVanishPolicy(boolean defaultHidden, Map<UUID, Boolean> overrides) {
    AutoVanishPolicy {
        overrides = Map.copyOf(overrides);
    }

    boolean hidden(boolean operator, UUID player) {
        return operator && overrides.getOrDefault(player, defaultHidden);
    }

    static AutoVanishPolicy load(ConfigurationSection config) {
        Object rawDefault = config.get("auto-vanish-default", true);
        if (!(rawDefault instanceof Boolean defaultHidden)) {
            throw new IllegalArgumentException("vanish.auto-vanish-default must be boolean");
        }
        Map<UUID, Boolean> overrides = new HashMap<>();
        if (config.contains("auto-vanish-overrides")) {
            ConfigurationSection section = config.getConfigurationSection("auto-vanish-overrides");
            if (section == null) throw new IllegalArgumentException("vanish.auto-vanish-overrides must be a UUID: boolean section");
            for (String key : section.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                    if (!uuid.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("vanish.auto-vanish-overrides: invalid UUID " + key);
                }
                if (!(section.get(key) instanceof Boolean value)) {
                    throw new IllegalArgumentException("vanish.auto-vanish-overrides." + key + " must be boolean");
                }
                if (overrides.put(uuid, value) != null) throw new IllegalArgumentException("Duplicate vanish UUID " + key);
            }
        }
        return new AutoVanishPolicy(defaultHidden, overrides);
    }
}
