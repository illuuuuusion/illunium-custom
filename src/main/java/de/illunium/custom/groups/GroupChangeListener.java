package de.illunium.custom.groups;

/** Main-thread notification after every committed mutation; null means created/deleted. */
@FunctionalInterface
public interface GroupChangeListener {
    void changed(Group oldGroup, Group newGroup);
}
