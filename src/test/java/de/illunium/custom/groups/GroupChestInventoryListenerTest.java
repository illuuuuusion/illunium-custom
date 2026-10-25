package de.illunium.custom.groups;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

class GroupChestInventoryListenerTest {
    @Test void topAllowsOnlyOrdinaryPickupsAndShiftWithdrawals() {
        var allowed = Set.of("NOTHING", "PICKUP_ALL", "PICKUP_SOME", "PICKUP_HALF", "PICKUP_ONE", "MOVE_TO_OTHER_INVENTORY");
        for (var action : InventoryAction.values()) assertEquals(allowed.contains(action.name()),
                GroupChestInventoryListener.allowsWithdrawal(action, ClickType.LEFT, true), action.name());
    }
    @Test void bottomShiftAndCollectAndAllSwapCreativePathsAreDenied() {
        assertFalse(GroupChestInventoryListener.allowsWithdrawal(InventoryAction.MOVE_TO_OTHER_INVENTORY, ClickType.SHIFT_LEFT, false));
        assertTrue(GroupChestInventoryListener.allowsWithdrawal(InventoryAction.PLACE_ALL, ClickType.LEFT, false));
        assertTrue(GroupChestInventoryListener.allowsWithdrawal(InventoryAction.SWAP_WITH_CURSOR, ClickType.RIGHT, false));
        for (var action : InventoryAction.values()) {
            for (var click : new ClickType[]{ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND, ClickType.CREATIVE,
                    ClickType.MIDDLE, ClickType.DOUBLE_CLICK, ClickType.UNKNOWN}) {
                assertFalse(GroupChestInventoryListener.allowsWithdrawal(action, click, true), action + "/" + click);
                assertFalse(GroupChestInventoryListener.allowsWithdrawal(action, click, false), action + "/" + click);
            }
        }
    }
}
