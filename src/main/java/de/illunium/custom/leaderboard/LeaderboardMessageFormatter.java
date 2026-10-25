package de.illunium.custom.leaderboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** Presentation only: receives existing ranks/values without changing statistics or filtering. */
final class LeaderboardMessageFormatter {
    private static final TextColor AQUA = TextColor.color(0x4DD0E1), GOLD = TextColor.color(0xFFD54F),
            SILVER = TextColor.color(0xBFC7D5), BRONZE = TextColor.color(0xCD9B6D),
            MUTED = TextColor.color(0xAEB7C2), VALUE = TextColor.color(0x9EE6A4);

    private LeaderboardMessageFormatter() { }

    static TextColor accent(LeaderboardDefinition definition) {
        return switch (definition) {
            case DIAMONDS, DISTANCE, FISHING -> AQUA;
            case PLAYTIME, SPAWN -> NamedTextColor.LIGHT_PURPLE;
            case PEAKLEVEL, MOBS -> VALUE;
            case TOTEMS -> GOLD;
            case DEATHS -> NamedTextColor.RED;
            case PLACED, MINED -> SILVER;
        };
    }

    static Component header(LeaderboardDefinition definition) {
        return Component.text("Illunium", AQUA).append(Component.text(" » ", GOLD))
                .append(Component.text(definition == null ? "Survival-Leaderboards" : "Leaderboard: ", NamedTextColor.WHITE))
                .append(definition == null ? Component.empty() : Component.text(definition.title, accent(definition)));
    }

    static Component overviewEntry(LeaderboardDefinition definition, boolean available) {
        return Component.text(definition.title, available ? accent(definition) : MUTED)
                .append(Component.text(" — /leaderboard " + definition.key() + (available ? "" : " (nicht verfügbar)"), NamedTextColor.GRAY))
                .clickEvent(ClickEvent.runCommand("/leaderboard " + definition.key()));
    }

    static Component entry(LeaderboardDefinition definition, RankingSnapshot.Row row) {
        TextColor rank = switch (row.rank()) { case 1 -> GOLD; case 2 -> SILVER; case 3 -> BRONZE; default -> MUTED; };
        return Component.text("#" + row.rank(), rank).append(Component.text("  " + row.name(), NamedTextColor.WHITE))
                .append(Component.text(" — ", NamedTextColor.GRAY)).append(Component.text(definition.format(row.value()), VALUE));
    }

    static Component ownRank(LeaderboardDefinition definition, RankingSnapshot.Row row) {
        return Component.text("Dein Platz: #" + row.rank(), AQUA).append(Component.text(" — ", NamedTextColor.GRAY))
                .append(Component.text(definition.format(row.value()), VALUE));
    }

    static Component navigation(LeaderboardDefinition definition, int page, int pages) {
        Component line = Component.text("Seite " + page + "/" + pages, NamedTextColor.GRAY);
        if (page > 1) line = line.append(Component.text(" · [Zurück]", AQUA)
                .clickEvent(ClickEvent.runCommand("/leaderboard " + definition.key() + " " + (page - 1))));
        if (page < pages) line = line.append(Component.text(" · [Weiter]", AQUA)
                .clickEvent(ClickEvent.runCommand("/leaderboard " + definition.key() + " " + (page + 1))));
        return line;
    }
}
