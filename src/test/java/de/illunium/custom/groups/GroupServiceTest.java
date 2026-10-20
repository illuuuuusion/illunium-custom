package de.illunium.custom.groups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.illunium.custom.TestMainThread;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.storage.Database;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroupServiceTest {

    private static final UUID LEADER = uuid(1);
    private static final UUID MOD = uuid(2);
    private static final UUID MEMBER = uuid(3);
    private static final UUID OUTSIDER = uuid(4);
    private static final UUID OTHER_LEADER = uuid(5);

    @TempDir
    Path dir;

    private final TestMainThread main = new TestMainThread();
    /** Last reported group per player, null = no group; mirrors what the appearance layer receives. */
    private final Map<UUID, Optional<Group>> shown = new HashMap<>();
    private Database db;
    private GroupService groups;

    @BeforeEach
    void open() throws Exception {
        db = Database.open(dir.resolve("data.db"));
        groups = load();
    }

    @AfterEach
    void close() {
        db.close();
    }

    @Test
    void namesAndTagsAreValidatedAndCaseInsensitiveUnique() {
        main.await(groups.create(LEADER, "NOVA", "NOVA"));

        assertEquals("A group with that name already exists.", main.fails(groups.create(OTHER_LEADER, "nova", "X")));
        assertEquals("A group with that tag already exists.", main.fails(groups.create(OTHER_LEADER, "Other", "nova")));
        for (String bad : List.of("", "has space", "a".repeat(17), "§cRed", "tab\t", "Ünicode", "zero​width")) {
            assertTrue(main.fails(groups.create(OTHER_LEADER, bad, "OK")).startsWith("Group names"), bad);
        }
        for (String bad : List.of("", "a b", "TOOLON", "§a", "\u0000")) {
            assertTrue(main.fails(groups.create(OTHER_LEADER, "Fine", bad)).startsWith("Tags"), bad);
        }
        Group max = main.await(groups.create(OTHER_LEADER, "a".repeat(16), "AB-1_"));
        assertEquals("a".repeat(16), max.name());
        assertEquals("You are already in a group.", main.fails(groups.create(LEADER, "Second", "SEC")));
    }

    @Test
    void createdGroupHasOneLeaderAndDefaults() {
        Group g = main.await(groups.create(LEADER, "Nova_1", "NOVA"));
        assertEquals(LEADER, g.leader());
        assertEquals(Map.of(LEADER, GroupRole.LEADER), g.members());
        assertEquals(NamedTextColor.WHITE, g.color());
        assertEquals(Optional.of(g), shown.get(LEADER));
        assertEquals(Optional.of(g), groups.groupOf(LEADER));
    }

    @Test
    void inviteIsValidFor299SecondsAndExpiresAt300() {
        Group g = main.await(groups.create(LEADER, "Nova", "NOVA"));
        main.await(groups.invite(LEADER, MEMBER));
        main.await(groups.invite(LEADER, OUTSIDER));

        main.advance(Duration.ofSeconds(299));
        main.await(groups.accept(MEMBER, "nova"));
        assertEquals(GroupRole.MEMBER, groups.groupOf(MEMBER).orElseThrow().role(MEMBER));

        main.advance(Duration.ofSeconds(1));
        assertThrows(UserException.class, () -> groups.accept(OUTSIDER, "Nova"));
        assertEquals(List.of(), groups.invitesFor(OUTSIDER));
        assertEquals(2, groups.groupOf(LEADER).orElseThrow().members().size());
        assertEquals(g.id(), groups.groupOf(MEMBER).orElseThrow().id());
    }

    @Test
    void acceptRevalidatesAndClearsOtherInvites() {
        main.await(groups.create(LEADER, "Nova", "NOVA"));
        main.await(groups.create(OTHER_LEADER, "Vega", "VEGA"));
        join(MOD, "Nova");
        main.await(groups.promote(LEADER, MOD));

        main.await(groups.invite(MOD, MEMBER));
        main.await(groups.invite(OTHER_LEADER, MEMBER));
        assertEquals(2, groups.invitesFor(MEMBER).size());

        // Inviter lost the right to invite -> invite no longer valid.
        main.await(groups.demote(LEADER, MOD));
        assertEquals("This invite is no longer valid.", main.fails(groups.accept(MEMBER, "Nova")));

        main.await(groups.accept(MEMBER, "Vega"));
        assertEquals(List.of(), groups.invitesFor(MEMBER));
        assertEquals("That player is already in a group.", main.fails(groups.invite(LEADER, MEMBER)));
    }

    @Test
    void inviteRulesAndDeleteDropsInvites() {
        main.await(groups.create(LEADER, "Nova", "NOVA"));
        join(MEMBER, "Nova");
        assertEquals("Only the leader and mods can invite.", main.fails(groups.invite(MEMBER, OUTSIDER)));
        assertEquals("You are not in a group.", main.fails(groups.invite(OUTSIDER, MEMBER)));

        main.await(groups.invite(LEADER, OUTSIDER));
        assertEquals("Only the leader and mods can withdraw invites.", main.fails(groups.uninvite(MEMBER, OUTSIDER)));
        main.await(groups.uninvite(LEADER, OUTSIDER));
        assertEquals("That player has no pending invite from your group.", main.fails(groups.uninvite(LEADER, OUTSIDER)));

        main.await(groups.invite(LEADER, OUTSIDER));
        assertEquals("Nova", groups.deny(OUTSIDER, "NOVA").name());
        assertThrows(UserException.class, () -> groups.deny(OUTSIDER, "Nova"));

        main.await(groups.invite(LEADER, OUTSIDER));
        main.await(groups.requestDisband(LEADER));
        main.await(groups.confirm(LEADER));
        assertEquals(List.of(), groups.invitesFor(OUTSIDER));
    }

    @Test
    void roleRulesAreEnforcedOnCommittedState() {
        Group g = setUpFullGroup();

        assertEquals("You may not kick that member.", main.fails(groups.kick(MEMBER, MOD)));
        assertEquals("You may not kick that member.", main.fails(groups.kick(MOD, MOD)));
        assertEquals("You may not kick that member.", main.fails(groups.kick(MOD, LEADER)));
        assertEquals("You may not kick that member.", main.fails(groups.kick(LEADER, LEADER)));
        assertEquals("That player is not in your group.", main.fails(groups.kick(LEADER, OUTSIDER)));
        assertEquals("Only the leader can promote members to mod.", main.fails(groups.promote(MOD, MEMBER)));
        assertEquals("Only the leader can promote members to mod.", main.fails(groups.promote(LEADER, MOD)));
        assertEquals("Only the leader can demote mods.", main.fails(groups.demote(LEADER, MEMBER)));
        assertEquals("Only the leader can demote mods.", main.fails(groups.demote(MOD, MOD)));
        assertEquals("Only the leader can change name, tag or colour.", main.fails(groups.rename(MOD, "X")));
        assertEquals("Only the leader can change name, tag or colour.", main.fails(groups.retag(MEMBER, "X")));
        assertEquals("Only the leader can change name, tag or colour.", main.fails(groups.recolor(MOD, "red")));
        assertEquals("Only the leader can transfer leadership, to another member.", main.fails(groups.requestTransfer(MOD, MEMBER)));
        assertEquals("Only the leader can transfer leadership, to another member.", main.fails(groups.requestTransfer(LEADER, LEADER)));
        assertEquals("Only the leader can disband the group.", main.fails(groups.requestDisband(MOD)));
        assertEquals("As leader, use /group transfer or /group disband first.", main.fails(groups.leave(LEADER)));
        assertEquals(g, groups.groupOf(LEADER).orElseThrow());

        main.await(groups.kick(MOD, MEMBER));
        assertTrue(groups.groupOf(MEMBER).isEmpty());
        assertEquals(Optional.empty(), shown.get(MEMBER));
        main.await(groups.kick(LEADER, MOD));
        assertEquals(Map.of(LEADER, GroupRole.LEADER), groups.groupOf(LEADER).orElseThrow().members());
    }

    @Test
    void memberAndModCanLeave() {
        setUpFullGroup();
        main.await(groups.leave(MOD));
        main.await(groups.leave(MEMBER));
        assertEquals(1, groups.groupOf(LEADER).orElseThrow().members().size());
        assertEquals("You are not in a group.", main.fails(groups.leave(MEMBER)));
    }

    @Test
    void leaderEditsAreUniqueAndOnlyLookChangesAreReported() {
        setUpFullGroup();
        main.await(groups.create(OTHER_LEADER, "Vega", "VEGA"));

        assertEquals("A group with that name already exists.", main.fails(groups.rename(LEADER, "vega")));
        assertEquals("A group with that tag already exists.", main.fails(groups.retag(LEADER, "Vega")));
        assertTrue(main.fails(groups.recolor(LEADER, "pink")).startsWith("Unknown colour"));

        shown.clear();
        assertEquals("nova", main.await(groups.rename(LEADER, "nova")).name());
        main.await(groups.promote(LEADER, MEMBER));
        assertTrue(shown.isEmpty(), "rename/promote must not touch appearance: " + shown);

        Group recolored = main.await(groups.recolor(LEADER, "DARK_RED"));
        assertEquals(NamedTextColor.DARK_RED, recolored.color());
        assertEquals(3, shown.size());
        assertTrue(main.fails(groups.recolor(LEADER, "#GG0000")).startsWith("Unknown colour"));
        assertEquals(NamedTextColor.DARK_RED, groups.groupOf(LEADER).orElseThrow().color(), "rejected colour changes nothing");
        assertEquals(TextColor.color(0xFF8800), main.await(groups.recolor(LEADER, "#fF8800")).color());
        assertEquals(TextColor.color(0xFF8800), reload().groupOf(MEMBER).orElseThrow().color(), "hex survives restart");
        assertEquals("NV", main.await(groups.retag(LEADER, "NV")).tag());
        assertEquals("NV", shown.get(MEMBER).orElseThrow().tag());
    }

    @Test
    void transferNeedsConfirmationWithin30Seconds() {
        Group g = setUpFullGroup();
        main.await(groups.requestTransfer(LEADER, MEMBER));
        main.advance(Duration.ofSeconds(30));
        assertThrows(UserException.class, () -> groups.confirm(LEADER));
        assertEquals(LEADER, groups.groupOf(LEADER).orElseThrow().leader());

        main.await(groups.requestTransfer(LEADER, MEMBER));
        main.advance(Duration.ofSeconds(29));
        GroupService.Confirmed done = main.await(groups.confirm(LEADER));
        assertEquals(new GroupService.Transfer(g.id(), MEMBER), done.action());
        Group after = groups.groupOf(LEADER).orElseThrow();
        assertEquals(MEMBER, after.leader());
        assertEquals(Map.of(MEMBER, GroupRole.LEADER, LEADER, GroupRole.MOD, MOD, GroupRole.MOD), after.members());
        assertEquals(after, reload().groupOf(MEMBER).orElseThrow());

        main.await(groups.requestDisband(MEMBER));
        assertEquals(new GroupService.Disband(g.id()), groups.cancel(MEMBER));
        assertThrows(UserException.class, () -> groups.confirm(MEMBER));
    }

    @Test
    void confirmRechecksTarget() {
        setUpFullGroup();
        main.await(groups.requestTransfer(LEADER, MEMBER));
        main.await(groups.leave(MEMBER));
        assertEquals("That player is no longer in your group.", main.fails(groups.confirm(LEADER)));
        assertEquals(LEADER, groups.groupOf(LEADER).orElseThrow().leader());
    }

    @Test
    void disbandRemovesGroupAndAllMemberships() {
        Group g = setUpFullGroup();
        main.await(groups.requestDisband(LEADER));
        GroupService.Confirmed done = main.await(groups.confirm(LEADER));
        assertEquals("Nova", done.group().name());
        assertTrue(groups.all().isEmpty());
        for (UUID member : g.members().keySet()) {
            assertTrue(groups.groupOf(member).isEmpty());
            assertEquals(Optional.empty(), shown.get(member));
        }
        assertEquals(0L, count("SELECT COUNT(*) FROM group_memberships"));
        main.await(groups.create(MOD, "Nova", "NOVA"));
    }

    @Test
    void concurrentAcceptsJoinExactlyOneGroup() {
        main.await(groups.create(LEADER, "Nova", "NOVA"));
        main.await(groups.create(OTHER_LEADER, "Vega", "VEGA"));
        main.await(groups.invite(LEADER, MEMBER));
        main.await(groups.invite(OTHER_LEADER, MEMBER));

        // Both pass the main-thread checks before either commits.
        CompletableFuture<Group> first = groups.accept(MEMBER, "Nova");
        CompletableFuture<Group> second = groups.accept(MEMBER, "Vega");
        main.await(first);
        assertEquals("You are already in a group.", main.fails(second));
        assertEquals("Nova", groups.groupOf(MEMBER).orElseThrow().name());
        assertEquals(1L, count("SELECT COUNT(*) FROM group_memberships WHERE player_uuid = '" + MEMBER + "'"));
    }

    @Test
    void concurrentTransferAndAdminDisbandLeaveNoHalfState() {
        Group g = setUpFullGroup();
        main.await(groups.requestTransfer(LEADER, MEMBER));
        CompletableFuture<Group> disband = groups.adminDisband(g.id());
        CompletableFuture<GroupService.Confirmed> transfer = groups.confirm(LEADER);
        main.await(disband);
        assertEquals("You are not in a group.", main.fails(transfer));
        assertEquals(0L, count("SELECT COUNT(*) FROM groups"));
        assertEquals(0L, count("SELECT COUNT(*) FROM group_memberships"));
    }

    @Test
    void offlineChangesShowOnRejoinAndRestartDropsInvites() {
        Group g = setUpFullGroup();
        main.await(groups.kick(LEADER, MEMBER));
        main.await(groups.invite(LEADER, OUTSIDER));

        GroupService restarted = reload();
        assertTrue(restarted.groupOf(MEMBER).isEmpty(), "kicked while offline stays kicked");
        assertEquals(GroupRole.MOD, restarted.groupOf(MOD).orElseThrow().role(MOD));
        assertEquals(List.of(), restarted.invitesFor(OUTSIDER));
        assertThrows(UserException.class, () -> restarted.accept(OUTSIDER, g.name()));
    }

    @Test
    void adminSetLeaderRequiresMember() {
        Group g = setUpFullGroup();
        assertEquals("The new leader must be a member of the group.", main.fails(groups.adminSetLeader(g.id(), OUTSIDER)));
        assertEquals("That player already leads the group.", main.fails(groups.adminSetLeader(g.id(), LEADER)));
        Group after = main.await(groups.adminSetLeader(g.id(), MEMBER));
        assertEquals(MEMBER, after.leader());
        assertEquals(GroupRole.MOD, after.role(LEADER));
        assertEquals(after, main.await(groups.inspect(g.id())).orElseThrow());
    }

    /** Nova: LEADER leads, MOD is mod, MEMBER is member. */
    private Group setUpFullGroup() {
        main.await(groups.create(LEADER, "Nova", "NOVA"));
        join(MOD, "Nova");
        join(MEMBER, "Nova");
        Group g = main.await(groups.promote(LEADER, MOD));
        assertEquals(Map.of(LEADER, GroupRole.LEADER, MOD, GroupRole.MOD, MEMBER, GroupRole.MEMBER), g.members());
        return g;
    }

    private void join(UUID player, String group) {
        UUID leader = groups.byName(group).orElseThrow().leader();
        main.await(groups.invite(leader, player));
        main.await(groups.accept(player, group));
    }

    private GroupService load() {
        return GroupService.load(db, main, main.clock(), (player, group) -> shown.put(player, Optional.ofNullable(group)));
    }

    private GroupService reload() {
        return load();
    }

    private long count(String sql) {
        return db.transaction(c -> {
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
                return r.getLong(1);
            }
        }).join();
    }

    private static UUID uuid(int n) {
        return UUID.fromString("00000000-0000-0000-0000-00000000000" + n);
    }

    @Test
    void unknownGroupAcceptFailsWithoutInvite() {
        UserException e = assertThrows(UserException.class, () -> groups.accept(OUTSIDER, "Nope"));
        assertEquals("You have no pending invite from group Nope.", e.getMessage());
        assertNull(shown.get(OUTSIDER));
        assertFalse(groups.byName("Nope").isPresent());
    }
}
