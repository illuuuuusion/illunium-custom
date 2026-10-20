package de.illunium.custom.groups;

import static de.illunium.custom.groups.GroupRole.LEADER;
import static de.illunium.custom.groups.GroupRole.MEMBER;
import static de.illunium.custom.groups.GroupRole.MOD;

/** The role matrix from MVP §3.2: what a role may do, optionally to a member with a given role. */
public final class GroupPolicy {

    public enum Action {
        VIEW, INVITE, UNINVITE, KICK, PROMOTE, DEMOTE, RENAME, TAG, COLOR, TRANSFER, DISBAND, LEAVE
    }

    private GroupPolicy() {
    }

    /** @param target role of the affected member for KICK/PROMOTE/DEMOTE/TRANSFER, otherwise ignored */
    public static boolean allows(GroupRole actor, Action action, GroupRole target) {
        return switch (action) {
            case VIEW -> true;
            case INVITE, UNINVITE -> actor == LEADER || actor == MOD;
            case KICK -> actor == LEADER ? target == MOD || target == MEMBER : actor == MOD && target == MEMBER;
            case PROMOTE -> actor == LEADER && target == MEMBER;
            case DEMOTE -> actor == LEADER && target == MOD;
            case TRANSFER -> actor == LEADER && (target == MOD || target == MEMBER);
            case RENAME, TAG, COLOR, DISBAND -> actor == LEADER;
            case LEAVE -> actor == MOD || actor == MEMBER;
        };
    }
}
