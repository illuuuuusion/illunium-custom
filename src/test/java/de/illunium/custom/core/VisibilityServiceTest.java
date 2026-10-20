package de.illunium.custom.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisibilityServiceTest {

    private static final UUID ADMIN = new UUID(0, 1);

    @Test
    void everyoneIsVisibleUntilHiddenAndChangesAreReportedOnce() {
        VisibilityService visibility = new VisibilityService(null);
        List<UUID> changes = new ArrayList<>();
        visibility.onChange(changes::add);
        assertTrue(visibility.isPubliclyVisible(ADMIN), "without vanish everyone is visible");

        assertTrue(visibility.setHidden(ADMIN, true));
        assertFalse(visibility.setHidden(ADMIN, true), "hiding twice is a no-op");
        assertFalse(visibility.isPubliclyVisible(ADMIN));
        assertFalse(visibility.publiclyOnline(ADMIN), "hidden is never online, without asking the server");
        assertEquals(Set.of(ADMIN), visibility.hidden());

        assertTrue(visibility.setHidden(ADMIN, false));
        assertFalse(visibility.setHidden(ADMIN, false), "showing twice is a no-op");
        assertEquals(List.of(ADMIN, ADMIN), changes);

        visibility.setHidden(ADMIN, true);
        visibility.forget(ADMIN);
        assertTrue(visibility.isPubliclyVisible(ADMIN));
        assertEquals(3, changes.size(), "quit is not a visibility change");
    }
}
