package de.illunium.custom.emotes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Replaces whole {@code :alias:} tokens with a configured symbol, case-insensitively, in one left-to-right pass:
 * output is never scanned again, unknown tokens stay exactly as typed. Plain text in, plain text out (no MiniMessage,
 * legacy codes or JSON). Immutable, so it is safe on the async chat thread.
 */
public final class EmoteFormatter {

    static final int MAX_ALIASES = 64;
    static final int MAX_SYMBOL_CODEPOINTS = 8;
    private static final Pattern ALIAS = Pattern.compile("[a-z0-9_+-]{1,32}");
    private static final Pattern TOKEN = Pattern.compile(":([A-Za-z0-9_+-]{1,32}):");

    public static final EmoteFormatter NONE = new EmoteFormatter(Map.of());

    static final Map<String, String> DEFAULTS = defaults();

    private final Map<String, String> symbols;

    EmoteFormatter(Map<String, String> symbols) {
        this.symbols = Map.copyOf(symbols);
    }

    public String format(String text) {
        if (symbols.isEmpty() || text.indexOf(':') < 0) return text;
        StringBuilder out = new StringBuilder(text.length());
        Matcher token = TOKEN.matcher(text);
        int from = 0;
        while (token.find(from)) {
            String symbol = symbols.get(token.group(1).toLowerCase(Locale.ROOT));
            if (symbol == null) {
                // Keep ":name" and let its closing colon open the next token, e.g. ":x:skull:" -> ":x💀".
                out.append(text, from, token.end(1));
                from = token.end(1);
            } else {
                out.append(text, from, token.start()).append(symbol);
                from = token.end();
            }
        }
        return out.append(text, from, text.length()).toString();
    }

    /** {@code :alias:} for each alias, sorted. */
    public List<String> completions() {
        return symbols.keySet().stream().sorted().map(alias -> ":" + alias + ":").toList();
    }

    /** {@code null} (section absent) gives the built-in defaults. */
    static EmoteFormatter parse(ConfigurationSection aliases) {
        if (aliases == null) return new EmoteFormatter(DEFAULTS);
        Map<String, String> symbols = new LinkedHashMap<>();
        for (String key : aliases.getKeys(false)) {
            String alias = key.toLowerCase(Locale.ROOT);
            if (!ALIAS.matcher(alias).matches())
                throw new IllegalArgumentException("emotes.aliases." + key + ": alias must be 1-32 of a-z 0-9 _ + -");
            if (!(aliases.get(key) instanceof String symbol) || !validSymbol(symbol))
                throw new IllegalArgumentException("emotes.aliases." + key + ": symbol must be 1-" + MAX_SYMBOL_CODEPOINTS
                        + " visible characters without ':', '§' or control characters");
            if (symbols.put(alias, symbol) != null)
                throw new IllegalArgumentException("emotes.aliases." + key + ": duplicate alias (aliases are case-insensitive)");
        }
        if (symbols.size() > MAX_ALIASES)
            throw new IllegalArgumentException("emotes.aliases: at most " + MAX_ALIASES + " aliases");
        return new EmoteFormatter(symbols);
    }

    private static boolean validSymbol(String symbol) {
        int length = symbol.codePointCount(0, symbol.length());
        return length >= 1 && length <= MAX_SYMBOL_CODEPOINTS && !symbol.isBlank()
                && symbol.codePoints().noneMatch(c -> c == ':' || c == '§' || Character.isISOControl(c)
                        || Character.getType(c) == Character.FORMAT && c != 0x200D); // ZWJ joins emoji
    }

    private static Map<String, String> defaults() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("skull", "💀");
        map.put("heart", "❤");
        map.put("broken_heart", "💔");
        map.put("smile", "☺");
        map.put("sad", "☹");
        map.put("star", "★");
        map.put("check", "✔");
        map.put("cross", "✖");
        map.put("sun", "☀");
        map.put("music", "♫");
        return Map.copyOf(map);
    }
}
