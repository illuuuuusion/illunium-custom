package de.illunium.custom.groups;

import static de.illunium.custom.groups.GroupPolicy.Action.COLOR;
import static de.illunium.custom.groups.GroupPolicy.Action.DEMOTE;
import static de.illunium.custom.groups.GroupPolicy.Action.DISBAND;
import static de.illunium.custom.groups.GroupPolicy.Action.INVITE;
import static de.illunium.custom.groups.GroupPolicy.Action.KICK;
import static de.illunium.custom.groups.GroupPolicy.Action.LEAVE;
import static de.illunium.custom.groups.GroupPolicy.Action.PROMOTE;
import static de.illunium.custom.groups.GroupPolicy.Action.RENAME;
import static de.illunium.custom.groups.GroupPolicy.Action.TAG;
import static de.illunium.custom.groups.GroupPolicy.Action.TRANSFER;
import static de.illunium.custom.groups.GroupPolicy.Action.UNINVITE;
import static de.illunium.custom.groups.GroupPolicy.Action.VIEW;
import static de.illunium.custom.groups.GroupRole.LEADER;
import static de.illunium.custom.groups.GroupRole.MEMBER;
import static de.illunium.custom.groups.GroupRole.MOD;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.illunium.custom.groups.GroupPolicy.Action;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GroupPolicyTest {

    private record Rule(GroupRole actor, Action action, GroupRole target) {
    }

    private static final Set<Action> TARGETED = EnumSet.of(KICK, PROMOTE, DEMOTE, TRANSFER);

    /** MVP §3.2, written out independently of the implementation. Everything not listed is denied. */
    private static final Set<Rule> ALLOWED = Set.of(
            new Rule(LEADER, VIEW, null), new Rule(MOD, VIEW, null), new Rule(MEMBER, VIEW, null),
            new Rule(LEADER, INVITE, null), new Rule(MOD, INVITE, null),
            new Rule(LEADER, UNINVITE, null), new Rule(MOD, UNINVITE, null),
            new Rule(LEADER, KICK, MEMBER), new Rule(MOD, KICK, MEMBER), new Rule(LEADER, KICK, MOD),
            new Rule(LEADER, RENAME, null), new Rule(LEADER, TAG, null), new Rule(LEADER, COLOR, null),
            new Rule(LEADER, PROMOTE, MEMBER),
            new Rule(LEADER, DEMOTE, MOD),
            new Rule(LEADER, TRANSFER, MOD), new Rule(LEADER, TRANSFER, MEMBER),
            new Rule(LEADER, DISBAND, null),
            new Rule(MOD, LEAVE, null), new Rule(MEMBER, LEAVE, null)
    );

    @Test
    void everyMatrixCellMatchesTheSpec() {
        int cells = 0;
        for (GroupRole actor : GroupRole.values()) {
            for (Action action : Action.values()) {
                GroupRole[] targets = TARGETED.contains(action) ? GroupRole.values() : new GroupRole[]{null};
                for (GroupRole target : targets) {
                    boolean expected = ALLOWED.contains(new Rule(actor, action, target));
                    assertEquals(expected, GroupPolicy.allows(actor, action, target), actor + " " + action + " " + target);
                    cells++;
                }
            }
        }
        assertEquals(3 * (8 + 4 * 3), cells);
    }
}
