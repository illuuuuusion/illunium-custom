package de.illunium.custom.groups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.illunium.custom.core.command.UserException;
import java.util.List;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

class GroupColorsTest {

    @Test
    void acceptsAllNamedColoursCaseInsensitively() {
        assertEquals(16, NamedTextColor.NAMES.keys().size());
        for (NamedTextColor named : NamedTextColor.NAMES.values()) {
            assertEquals(named, GroupColors.parse(named.toString().toUpperCase()));
            assertEquals(named, GroupColors.read(GroupColors.serialize(named)), "v1.0 stores upper-case names");
        }
        assertEquals("LIGHT_PURPLE", GroupColors.serialize(GroupColors.parse("Light_Purple")));
    }

    @Test
    void acceptsSixDigitHexAndStoresItUpperCase() {
        assertEquals(NamedTextColor.BLACK, GroupColors.parse("#000000"));
        assertEquals("#A1B2C3", GroupColors.serialize(GroupColors.parse("#a1B2c3")));
        assertEquals("#FF8800", GroupColors.name(GroupColors.parse("#fF8800")));
        assertEquals(TextColor.color(0xA1B2C3), GroupColors.read("#A1B2C3"));
        assertEquals(NamedTextColor.WHITE, GroupColors.parse("#FFFFFF"), "hex of a named colour is that colour");
    }

    @Test
    void rejectsEverythingElse() {
        for (String bad : List.of("FF8800", "#FFF", "#FFFF", "#FFFFFFFF", "#GG0000", "#ZZ0000", "pink", "", " #FF8800",
                "#FF8800 ", "#FF8800\n", "<red>", "&c", "§c", "\u0000")) {
            assertThrows(UserException.class, () -> GroupColors.parse(bad), bad);
        }
        assertEquals(NamedTextColor.WHITE, GroupColors.read("garbage"));
    }
}
