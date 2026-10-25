package de.illunium.custom.groups;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.bukkit.inventory.ItemStack;

/** Versioned 27-slot envelope around Paper's data-versioned NBT. Call on the main thread. */
final class GroupChestCodec {
    static final int SLOTS = 27;
    private static final int MAGIC = 0x49434743; // ICGC
    private static final int VERSION = 1;

    private GroupChestCodec() { }

    static boolean present(ItemStack item) { return item != null && !item.isEmpty(); }

    static int occupied(ItemStack[] items) {
        int count = 0;
        for (ItemStack item : items) if (present(item)) count++;
        return count;
    }

    static byte[] encode(ItemStack[] items) {
        if (items.length != SLOTS) throw new IllegalArgumentException("Expected 27 slots");
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(SLOTS);
            for (ItemStack item : items) {
                out.writeByte(present(item) ? 1 : 0);
                if (present(item)) {
                    byte[] payload = item.serializeAsBytes();
                    out.writeInt(payload.length); out.write(payload);
                }
            }
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    static ItemStack[] decode(byte[] bytes) {
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != MAGIC || in.readInt() != VERSION || in.readInt() != SLOTS)
                throw new IOException("Unknown chest format/version/size");
            ItemStack[] items = new ItemStack[SLOTS];
            for (int slot = 0; slot < SLOTS; slot++) {
                int marker = in.readUnsignedByte();
                if (marker == 0) continue;
                if (marker != 1) throw new IOException("Invalid slot marker");
                int length = in.readInt();
                if (length <= 0 || length > in.available()) throw new IOException("Invalid item length");
                items[slot] = ItemStack.deserializeBytes(in.readNBytes(length));
                if (!present(items[slot])) throw new IOException("Item payload decoded as empty");
            }
            if (in.available() != 0) throw new IOException("Trailing chest data");
            return items;
        } catch (IOException | RuntimeException e) { throw new IllegalArgumentException("Unreadable group chest; keep the stored snapshot for recovery", e); }
    }
}
