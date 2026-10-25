package de.illunium.custom.groups;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Object identity identifies this live session, even if a numeric group ID is reused. */
final class GroupChestHolder implements InventoryHolder {
    private final long groupId;
    private final Inventory inventory;

    GroupChestHolder(Group group) {
        groupId = group.id();
        inventory = Bukkit.createInventory(this, GroupChestCodec.SLOTS,
                Component.text("Gruppenkiste • ").append(Component.text(group.tag(), group.color())));
    }

    long groupId() { return groupId; }
    @Override public Inventory getInventory() { return inventory; }
}
