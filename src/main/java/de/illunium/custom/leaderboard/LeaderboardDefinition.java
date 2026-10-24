package de.illunium.custom.leaderboard;

import java.util.Arrays;
import java.util.Locale;

public enum LeaderboardDefinition {
    DIAMONDS("Diamanterze abgebaut", "Erze", "Vanilla", "Vorhandene Vanilla-Historie"),
    PLACED("Blöcke platziert", "Blöcke", "Eigener Zähler", "Ab v1.3"),
    MINED("Blöcke abgebaut", "Blöcke", "Vanilla", "Vorhandene Vanilla-Historie"),
    PLAYTIME("Spielzeit", "", "Playtime-Modul", "Seit Einführung des Playtime-Moduls"),
    DISTANCE("Zu Fuß zurückgelegte Distanz", "", "Vanilla", "Vorhandene Vanilla-Historie"),
    MOBS("Mobs getötet", "Mobs", "Vanilla", "Vorhandene Vanilla-Historie"),
    DEATHS("Tode", "Tode", "Vanilla", "Vorhandene Vanilla-Historie"),
    TOTEMS("Totems ausgelöst", "Totems", "Eigener Zähler", "Ab v1.3"),
    SPAWN("/spawn verwendet", "Nutzungen", "Spawn-Erfolg", "Ab v1.3"),
    FISHING("Angel-Fänge", "Fänge", "Vanilla", "Vorhandene Vanilla-Historie"),
    PEAKLEVEL("Höchstes XP-Level", "Level", "Eigener Maximalwert", "Seit v1.3 beobachteter Rekord");

    public final String title;
    public final String unit;
    public final String source;
    public final String history;

    LeaderboardDefinition(String title, String unit, String source, String history) {
        this.title = title; this.unit = unit; this.source = source; this.history = history;
    }

    public String key() { return name().toLowerCase(Locale.ROOT); }
    public static LeaderboardDefinition find(String key) {
        return Arrays.stream(values()).filter(d -> d.key().equalsIgnoreCase(key)).findFirst().orElse(null);
    }

    public String format(long value) {
        if (this == PLAYTIME) {
            long days = value / 86400, hours = value % 86400 / 3600, minutes = value % 3600 / 60;
            return (days > 0 ? days + "d " : "") + (days > 0 || hours > 0 ? hours + "h " : "") + minutes + "m";
        }
        if (this == DISTANCE) return value >= 100000
                ? String.format(Locale.GERMANY, "%.2f km", value / 100000.0)
                : String.format(Locale.GERMANY, "%.2f m", value / 100.0);
        return String.format(Locale.GERMANY, "%,d %s", value, unit);
    }
}
