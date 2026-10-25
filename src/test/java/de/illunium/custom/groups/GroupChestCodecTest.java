package de.illunium.custom.groups;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class GroupChestCodecTest {
    @Test void exactly27EmptySlotsRoundTrip() {
        byte[] bytes = GroupChestCodec.encode(new ItemStack[27]);
        assertEquals(27, GroupChestCodec.decode(bytes).length);
        assertEquals(0, GroupChestCodec.occupied(GroupChestCodec.decode(bytes)));
        assertArrayEquals(bytes, GroupChestCodec.encode(GroupChestCodec.decode(bytes)));
        assertThrows(IllegalArgumentException.class, () -> GroupChestCodec.encode(new ItemStack[26]));
    }
    @Test void corruptAndFutureFormatsFailClosed() {
        byte[] valid = GroupChestCodec.encode(new ItemStack[27]);
        for (int length = 0; length < valid.length; length++) {
            byte[] truncated = Arrays.copyOf(valid, length);
            assertThrows(IllegalArgumentException.class, () -> GroupChestCodec.decode(truncated));
        }
        for (int offset : new int[]{0, 4, 8}) {
            byte[] changed = valid.clone(); ByteBuffer.wrap(changed).putInt(offset, 999);
            assertThrows(IllegalArgumentException.class, () -> GroupChestCodec.decode(changed));
        }
        byte[] marker = valid.clone(); marker[12] = 2;
        assertThrows(IllegalArgumentException.class, () -> GroupChestCodec.decode(marker));
        byte[] oversized = valid.clone(); oversized[12] = 1; ByteBuffer.wrap(oversized).putInt(13, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> GroupChestCodec.decode(oversized));
        assertThrows(IllegalArgumentException.class, () -> GroupChestCodec.decode(Arrays.copyOf(valid, valid.length + 1)));
    }
}
