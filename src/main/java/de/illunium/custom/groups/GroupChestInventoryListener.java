package de.illunium.custom.groups;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class GroupChestInventoryListener implements Listener {
    private final GroupChestService chests;
    GroupChestInventoryListener(GroupChestService chests) { this.chests = chests; }

    /** Allowlist: new Paper actions (including bundles) remain blocked until explicitly reviewed. */
    static boolean allowsWithdrawal(InventoryAction action, ClickType click, boolean top) {
        if (click == ClickType.CREATIVE || click == ClickType.MIDDLE || click == ClickType.DOUBLE_CLICK
                || click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND || click == ClickType.UNKNOWN) return false;
        return switch (action) {
            case NOTHING, PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE -> true;
            case MOVE_TO_OTHER_INVENTORY -> top;
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR,
                 DROP_ALL_CURSOR, DROP_ONE_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT -> !top;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        var inventory = event.getView().getTopInventory();
        if (!(inventory.getHolder(false) instanceof GroupChestHolder)) return;
        if (!chests.mayUse(event.getWhoClicked().getUniqueId(), inventory)) {
            event.setCancelled(true);
            chests.closeNextTick(event.getWhoClicked(), inventory);
        } else if (chests.withdrawalOnly(event.getWhoClicked().getUniqueId())
                && !allowsWithdrawal(event.getAction(), event.getClick(), event.getRawSlot() >= 0 && event.getRawSlot() < inventory.getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void clicked(InventoryClickEvent event) { chests.afterInteraction(event.getView().getTopInventory()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        var inventory = event.getView().getTopInventory();
        if (!(inventory.getHolder(false) instanceof GroupChestHolder)) return;
        if (!chests.mayUse(event.getWhoClicked().getUniqueId(), inventory)) {
            event.setCancelled(true);
            chests.closeNextTick(event.getWhoClicked(), inventory);
        } else if (chests.withdrawalOnly(event.getWhoClicked().getUniqueId())
                && event.getRawSlots().stream().anyMatch(slot -> slot < inventory.getSize())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dragged(InventoryDragEvent event) { chests.afterInteraction(event.getView().getTopInventory()); }

    @EventHandler public void closed(InventoryCloseEvent event) { chests.afterInteraction(event.getInventory()); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        var inventory = event.getPlayer().getOpenInventory().getTopInventory();
        if (inventory.getHolder(false) instanceof GroupChestHolder) event.getPlayer().closeInventory();
    }
}
