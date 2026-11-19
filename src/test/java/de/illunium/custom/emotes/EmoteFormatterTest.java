package de.illunium.custom.emotes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EmoteFormatterTest {

    private final EmoteFormatter emotes = new EmoteFormatter(EmoteFormatter.DEFAULTS);

    @Test
    void replacesWholeAliasesCaseInsensitivelyAndKeepsEverythingElse() {
        assertEquals("GG 💀", emotes.format("GG :skull:"));
        assertEquals("💀❤★", emotes.format(":skull::heart::STAR:"));
        assertEquals("a 💀 b 💀", emotes.format("a :Skull: b :SKULL:"));
        assertEquals(":skull", emotes.format(":skull"));
        assertEquals("skull:", emotes.format("skull:"));
        assertEquals(":skullish:", emotes.format(":skullish:"));
        assertEquals(":unknown: ok", emotes.format(":unknown: ok"));
        assertEquals(":x💀", emotes.format(":x:skull:"), "an unknown token's closing colon may open the next");
        assertEquals(": skull :", emotes.format(": skull :"));
        assertEquals("https://example.org:8080/a:b", emotes.format("https://example.org:8080/a:b"));
        assertEquals("<red>💀</red> &c §c", emotes.format("<red>:skull:</red> &c §c"), "no MiniMessage or legacy parsing");
        assertEquals("/g 💀", emotes.format("/g :skull:"), "commands are never chat, the text is formatted as given");
        assertEquals("", emotes.format(""));
        assertEquals(":::", emotes.format(":::"));
        assertEquals("💔", emotes.format(":broken_heart:"));
        assertEquals("plain", EmoteFormatter.NONE.format("plain"));
        assertEquals(":skull:", EmoteFormatter.NONE.format(":skull:"));
    }

    @Test
    void outputIsNotScannedAgain() {
        // A symbol cannot contain ':' (rejected by the parser), and an alias next to replaced output stays literal.
        var formatter = new EmoteFormatter(Map.of("a", "b"));
        assertEquals("b:a", formatter.format(":a::a"));
    }

    @Test
    void completionsAreSortedWithColons() {
        assertEquals(List.of(":broken_heart:", ":check:", ":cross:", ":heart:", ":music:", ":sad:", ":skull:", ":smile:", ":star:", ":sun:"),
                emotes.completions());
        assertEquals(List.of(), EmoteFormatter.NONE.completions());
    }

    @Test
    void parsesPackagedConfigAndDefaults() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var in = new java.io.InputStreamReader(getClass().getResourceAsStream("/config.yml"), java.nio.charset.StandardCharsets.UTF_8)) {
            yaml.load(in);
        }
        assertEquals(emotes.completions(), EmoteFormatter.parse(yaml.getConfigurationSection("emotes.aliases")).completions());
        assertEquals("GG 💀", EmoteFormatter.parse(null).format("GG :skull:"));
        assertEquals("☠", EmoteFormatter.parse(section("Skull: \"☠\"")).format(":skull:"));
        assertEquals("👍🏽", EmoteFormatter.parse(section("ok: \"👍🏽\"")).format(":ok:"));
        assertEquals("👨‍👩‍👧", EmoteFormatter.parse(section("family: \"👨‍👩‍👧\"")).format(":family:"), "ZWJ sequence");
    }

    @Test
    void rejectsInvalidAliasesAndSymbols() throws Exception {
        for (String bad : new String[]{"\"sk ull\": x", "\"sk:ull\": x", "skull: \"\"", "skull: \"  \"", "skull: \"a:b\"",
                "skull: \"§c\"", "skull: \"123456789\"", "skull: 5", "skull: {a: b}", "skull: \"\\u202E\"", "skull: \"\\n\"",
                "\"" + "a".repeat(33) + "\": x", "skull: x\nSKULL: y"}) {
            assertThrows(IllegalArgumentException.class, () -> EmoteFormatter.parse(section(bad)), bad);
        }
        StringBuilder many = new StringBuilder();
        for (int i = 0; i <= EmoteFormatter.MAX_ALIASES; i++) many.append("a").append(i).append(": x\n");
        var error = assertThrows(IllegalArgumentException.class, () -> EmoteFormatter.parse(section(many.toString())));
        assertTrue(error.getMessage().contains("at most"));
    }

    private static org.bukkit.configuration.ConfigurationSection section(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return config;
    }
}
