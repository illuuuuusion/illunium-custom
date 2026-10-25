package de.illunium.custom.groups;

import org.bukkit.configuration.ConfigurationSection;

record GroupSettings(boolean chestEnabled, boolean chatEnabled, int messageMaxLength, int cooldownSeconds) {
    static GroupSettings load(ConfigurationSection config) {
        for (String section : new String[]{"gec", "chat"})
            if (config.contains(section) && !config.isConfigurationSection(section))
                throw new IllegalArgumentException("groups." + section + " must be a section");
        integer(config, "gec.min-members", 4, 4, 4);
        integer(config, "gec.slots", 27, 27, 27);
        return new GroupSettings(bool(config, "gec.enabled"), bool(config, "chat.enabled"),
                integer(config, "chat.message-max-length", 256, 1, 256),
                integer(config, "chat.cooldown-seconds", 1, 0, 10));
    }

    private static boolean bool(ConfigurationSection config, String key) {
        Object value = config.get(key, true);
        if (value instanceof Boolean b) return b;
        throw new IllegalArgumentException("groups." + key + " must be true or false");
    }

    private static int integer(ConfigurationSection config, String key, int fallback, int min, int max) {
        Object value = config.get(key, fallback);
        if (value instanceof Integer n && n >= min && n <= max) return n;
        throw new IllegalArgumentException("groups." + key + " must be " + min + ".." + max);
    }
}
