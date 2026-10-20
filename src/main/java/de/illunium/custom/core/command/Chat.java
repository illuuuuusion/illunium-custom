package de.illunium.custom.core.command;

import java.util.Collection;
import java.util.Locale;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Message and completion helpers shared by the feature commands. */
public final class Chat {

    private Chat() {
    }

    public static Component ok(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    public static Component info(String message) {
        return Component.text(message, NamedTextColor.GRAY);
    }

    public static Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    /** Argument {@code index} or a {@link UserException} with the usage line. */
    public static String arg(String[] args, int index, String usage) {
        if (args.length <= index || args[index].isBlank()) {
            throw new UserException("Usage: " + usage);
        }
        return args[index];
    }

    /** Candidates starting with the last typed argument, case-insensitively. */
    public static Collection<String> complete(String[] args, Stream<String> candidates) {
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return candidates.filter(c -> c.toLowerCase(Locale.ROOT).startsWith(typed)).sorted().toList();
    }
}
