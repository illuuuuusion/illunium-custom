package de.illunium.custom.groups;

import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/** Immutable snapshot of a committed group. {@code members} includes the leader with role LEADER. */
public record Group(long id, String name, String tag, TextColor color, UUID leader, Map<UUID, GroupRole> members) {

    public GroupRole role(UUID uuid) {
        return members.get(uuid);
    }

    /** {@code [TAG] } in the group colour, used in nametag and tablist. */
    public Component prefix() {
        return Component.text("[" + tag + "] ", color);
    }

    boolean looksLike(Group other) {
        return tag.equals(other.tag) && color.equals(other.color);
    }
}
