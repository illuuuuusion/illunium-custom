package de.illunium.custom.playtimecolors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.format.NamedTextColor;

/** Ten playtime tiers (Patch v1.5 §3.1); every threshold is inclusive. Pure, no Bukkit. */
record PlaytimeColorPolicy(List<Tier> tiers) {

    static final int TIERS = 10;

    record Tier(long hours, NamedTextColor color) {
    }

    static final PlaytimeColorPolicy DEFAULT = new PlaytimeColorPolicy(List.of(
            new Tier(0, NamedTextColor.GRAY), new Tier(10, NamedTextColor.WHITE), new Tier(30, NamedTextColor.GREEN),
            new Tier(75, NamedTextColor.DARK_AQUA), new Tier(150, NamedTextColor.AQUA), new Tier(300, NamedTextColor.BLUE),
            new Tier(500, NamedTextColor.LIGHT_PURPLE), new Tier(800, NamedTextColor.RED), new Tier(1200, NamedTextColor.GOLD),
            new Tier(2000, NamedTextColor.YELLOW)));

    PlaytimeColorPolicy {
        if (tiers.size() != TIERS) throw new IllegalArgumentException("playtimecolors.tiers must have exactly " + TIERS + " entries");
        if (tiers.getFirst().hours() != 0) throw new IllegalArgumentException("playtimecolors.tiers must start at 0 hours");
        for (int i = 1; i < TIERS; i++) {
            if (tiers.get(i).hours() <= tiers.get(i - 1).hours())
                throw new IllegalArgumentException("playtimecolors.tiers hours must be strictly ascending");
        }
        tiers = List.copyOf(tiers);
    }

    /** 1-based tier for a total playtime in seconds. */
    int tier(long seconds) {
        int tier = 1;
        for (int i = 1; i < TIERS; i++) {
            if (seconds >= tiers.get(i).hours() * 3600) tier = i + 1;
        }
        return tier;
    }

    NamedTextColor color(long seconds) {
        return tiers.get(tier(seconds) - 1).color();
    }

    /** {@code null} (key absent) gives the default table. */
    static PlaytimeColorPolicy parse(List<Map<?, ?>> entries) {
        if (entries == null) return DEFAULT;
        List<Tier> tiers = new ArrayList<>();
        for (Map<?, ?> entry : entries) {
            if (!(entry.get("hours") instanceof Integer hours) || hours < 0)
                throw new IllegalArgumentException("playtimecolors.tiers: hours must be a whole number >= 0, got " + entry.get("hours"));
            NamedTextColor color = entry.get("color") instanceof String name ? NamedTextColor.NAMES.value(name.toLowerCase(Locale.ROOT)) : null;
            if (color == null)
                throw new IllegalArgumentException("playtimecolors.tiers: unknown color " + entry.get("color") + " (use e.g. GRAY, GOLD)");
            tiers.add(new Tier(hours, color));
        }
        return new PlaytimeColorPolicy(tiers);
    }
}
