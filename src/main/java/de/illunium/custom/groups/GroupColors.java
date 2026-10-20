package de.illunium.custom.groups;

import de.illunium.custom.core.command.UserException;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Group colours: one of the 16 named colours or {@code #RRGGBB}. Stored as the upper-case name (as in v1.0 data) or
 * as upper-case hex; a hex value equal to a named colour is that named colour.
 */
final class GroupColors {

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private GroupColors() {
    }

    /** Validated user input; names are case-insensitive, hex needs the {@code #} and exactly six digits. */
    static TextColor parse(String input) {
        TextColor color = lenient(input);
        if (color == null) {
            throw new UserException("Unknown colour. Use #RRGGBB or one of: "
                    + String.join(", ", new TreeSet<>(NamedTextColor.NAMES.keys())));
        }
        return color;
    }

    /** Stored value to colour; anything unreadable falls back to white, as before v1.1. */
    static TextColor read(String stored) {
        TextColor color = lenient(stored);
        return color == null ? NamedTextColor.WHITE : color;
    }

    /** Lower-case name or {@code #RRGGBB}, for display. */
    static String name(TextColor color) {
        return color instanceof NamedTextColor named ? named.toString() : color.asHexString().toUpperCase(Locale.ROOT);
    }

    static String serialize(TextColor color) {
        return name(color).toUpperCase(Locale.ROOT);
    }

    private static TextColor lenient(String value) {
        NamedTextColor named = NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        return HEX.matcher(value).matches() ? TextColor.fromHexString(value) : null;
    }
}
