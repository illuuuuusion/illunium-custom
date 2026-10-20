package de.illunium.custom.groups;

import de.illunium.custom.core.ExpiringMap;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.groups.GroupPolicy.Action;
import de.illunium.custom.storage.Database;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * Group rules. Every mutation re-reads the committed state and checks {@link GroupPolicy} inside its transaction,
 * so concurrent commands cannot leave half changes; the in-memory view is updated on the main thread only after
 * commit. Public methods are called on the main thread and either throw {@link UserException} or return a future
 * that may fail with one.
 */
public final class GroupService {

    static final Duration INVITE_TTL = Duration.ofMinutes(5);
    static final Duration CONFIRM_TTL = Duration.ofSeconds(30);
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_-]{1,16}$");
    private static final Pattern TAG = Pattern.compile("^[A-Za-z0-9_-]{1,5}$");

    record InviteKey(UUID invitee, long groupId) {
    }

    public sealed interface Confirmation {
        long groupId();
    }

    public record Transfer(long groupId, UUID target) implements Confirmation {
    }

    public record Disband(long groupId) implements Confirmation {
    }

    /** @param group after a transfer, or as it was before a disband */
    public record Confirmed(Confirmation action, Group group) {
    }

    private final Database database;
    private final Executor mainThread;
    private final BiConsumer<UUID, Group> membershipChanged;
    private final Map<Long, Group> groups = new HashMap<>();
    private final Map<UUID, Long> groupOfMember = new HashMap<>();
    private final ExpiringMap<InviteKey, UUID> invites;
    private final ExpiringMap<UUID, Confirmation> confirmations;

    /**
     * @param membershipChanged called on the main thread with a player and their group (null if none) whenever
     *                          they joined/left a group or its tag/colour changed
     */
    private GroupService(Database database, Executor mainThread, InstantSource clock, BiConsumer<UUID, Group> membershipChanged) {
        this.database = database;
        this.mainThread = mainThread;
        this.membershipChanged = membershipChanged;
        this.invites = new ExpiringMap<>(clock, INVITE_TTL);
        this.confirmations = new ExpiringMap<>(clock, CONFIRM_TTL);
    }

    /** Loads all groups; blocks, so call only during startup. */
    public static GroupService load(Database database, Executor mainThread, InstantSource clock,
                                    BiConsumer<UUID, Group> membershipChanged) {
        GroupService service = new GroupService(database, mainThread, clock, membershipChanged);
        database.transaction(GroupRepository::loadAll).join().forEach(service::apply);
        return service;
    }

    // --- reads (main thread, committed state) ---

    public Optional<Group> groupOf(UUID player) {
        return Optional.ofNullable(groupOfMember.get(player)).map(groups::get);
    }

    public Optional<Group> byName(String name) {
        return groups.values().stream().filter(g -> g.name().equalsIgnoreCase(name)).findFirst();
    }

    public Collection<Group> all() {
        return List.copyOf(groups.values());
    }

    /** Groups with a valid invite for {@code invitee}. */
    public List<Group> invitesFor(UUID invitee) {
        return invites.all().keySet().stream()
                .filter(k -> k.invitee().equals(invitee))
                .map(k -> groups.get(k.groupId()))
                .toList();
    }

    /** Players with a valid invite from {@code groupId}. */
    public List<UUID> invitees(long groupId) {
        return invites.all().keySet().stream().filter(k -> k.groupId() == groupId).map(InviteKey::invitee).toList();
    }

    // --- member commands ---

    public CompletableFuture<Group> create(UUID actor, String name, String tag) {
        long now = System.currentTimeMillis();
        return mutate(c -> {
            validate(name, tag);
            if (GroupRepository.ofMember(c, actor).isPresent()) {
                throw new UserException("You are already in a group.");
            }
            requireFree(c, "name", name, 0);
            requireFree(c, "tag", tag, 0);
            return GroupRepository.insert(c, name, tag, actor, now);
        });
    }

    public CompletableFuture<Group> invite(UUID actor, UUID target) {
        return mutate(c -> {
            Group group = groupOf(c, actor);
            require(GroupPolicy.allows(group.role(actor), Action.INVITE, null), "Only the leader and mods can invite.");
            if (GroupRepository.ofMember(c, target).isPresent()) {
                throw new UserException("That player is already in a group.");
            }
            return group.id();
        }).thenApply(group -> {
            invites.put(new InviteKey(target, group.id()), actor);
            return group;
        });
    }

    public CompletableFuture<Group> uninvite(UUID actor, UUID target) {
        return mutate(c -> {
            Group group = groupOf(c, actor);
            require(GroupPolicy.allows(group.role(actor), Action.UNINVITE, null), "Only the leader and mods can withdraw invites.");
            return group.id();
        }).thenApply(group -> {
            if (invites.remove(new InviteKey(target, group.id())).isEmpty()) {
                throw new UserException("That player has no pending invite from your group.");
            }
            return group;
        });
    }

    /** Re-checks expiry, that the group still exists, the inviter may still invite and the actor is still groupless. */
    public CompletableFuture<Group> accept(UUID actor, String groupName) {
        InviteKey key = inviteFrom(actor, groupName);
        UUID inviter = invites.get(key).orElseThrow();
        long now = System.currentTimeMillis();
        return mutate(c -> {
            Group group = GroupRepository.load(c, key.groupId())
                    .orElseThrow(() -> new UserException("That group no longer exists."));
            GroupRole inviterRole = group.role(inviter);
            require(inviterRole != null && GroupPolicy.allows(inviterRole, Action.INVITE, null),
                    "This invite is no longer valid.");
            if (GroupRepository.ofMember(c, actor).isPresent()) {
                throw new UserException("You are already in a group.");
            }
            GroupRepository.addMember(c, group.id(), actor, GroupRole.MEMBER, now);
            return group.id();
        }).thenApply(group -> {
            invites.removeIf((k, v) -> k.invitee().equals(actor));
            return group;
        });
    }

    public Group deny(UUID actor, String groupName) {
        InviteKey key = inviteFrom(actor, groupName);
        invites.remove(key);
        return groups.get(key.groupId());
    }

    public CompletableFuture<Group> leave(UUID actor) {
        return mutate(c -> {
            Group group = groupOf(c, actor);
            require(GroupPolicy.allows(group.role(actor), Action.LEAVE, null),
                    "As leader, use /group transfer or /group disband first.");
            GroupRepository.removeMember(c, actor);
            return group.id();
        });
    }

    public CompletableFuture<Group> kick(UUID actor, UUID target) {
        return onMember(actor, target, Action.KICK, "You may not kick that member.",
                c -> GroupRepository.removeMember(c, target));
    }

    public CompletableFuture<Group> promote(UUID actor, UUID target) {
        return onMember(actor, target, Action.PROMOTE, "Only the leader can promote members to mod.",
                c -> GroupRepository.setRole(c, target, GroupRole.MOD));
    }

    public CompletableFuture<Group> demote(UUID actor, UUID target) {
        return onMember(actor, target, Action.DEMOTE, "Only the leader can demote mods.",
                c -> GroupRepository.setRole(c, target, GroupRole.MEMBER));
    }

    public CompletableFuture<Group> rename(UUID actor, String name) {
        return leaderEdit(actor, Action.RENAME, c -> {
            requireMatches(NAME, name, "Group names are 1-16 characters: letters, digits, _ or -.");
            return name;
        }, "name");
    }

    public CompletableFuture<Group> retag(UUID actor, String tag) {
        return leaderEdit(actor, Action.TAG, c -> {
            requireMatches(TAG, tag, "Tags are 1-5 characters: letters, digits, _ or -.");
            return tag;
        }, "tag");
    }

    public CompletableFuture<Group> recolor(UUID actor, String colorName) {
        return leaderEdit(actor, Action.COLOR, c -> GroupColors.serialize(GroupColors.parse(colorName)), "color");
    }

    /** Stores a pending transfer for {@link #confirm}; nothing changes yet. */
    public CompletableFuture<Group> requestTransfer(UUID actor, UUID target) {
        return database.transaction(c -> {
            Group group = groupOf(c, actor);
            GroupRole targetRole = group.role(target);
            require(targetRole != null, "That player is not in your group.");
            require(GroupPolicy.allows(group.role(actor), Action.TRANSFER, targetRole),
                    "Only the leader can transfer leadership, to another member.");
            return group;
        }).thenApplyAsync(group -> {
            confirmations.put(actor, new Transfer(group.id(), target));
            return group;
        }, mainThread);
    }

    /** Stores a pending disband for {@link #confirm}; nothing changes yet. */
    public CompletableFuture<Group> requestDisband(UUID actor) {
        return database.transaction(c -> {
            Group group = groupOf(c, actor);
            require(GroupPolicy.allows(group.role(actor), Action.DISBAND, null), "Only the leader can disband the group.");
            return group;
        }).thenApplyAsync(group -> {
            confirmations.put(actor, new Disband(group.id()));
            return group;
        }, mainThread);
    }

    /** Executes the pending transfer/disband after re-checking it. */
    public CompletableFuture<Confirmed> confirm(UUID actor) {
        Confirmation pending = confirmations.remove(actor)
                .orElseThrow(() -> new UserException("Nothing to confirm (requests expire after 30 seconds)."));
        return mutate(c -> {
            Group group = groupOf(c, actor);
            require(group.id() == pending.groupId() && group.leader().equals(actor), "You are no longer the leader of that group.");
            switch (pending) {
                case Transfer t -> {
                    GroupRole targetRole = group.role(t.target());
                    require(GroupPolicy.allows(GroupRole.LEADER, Action.TRANSFER, targetRole), "That player is no longer in your group.");
                    GroupRepository.setLeader(c, group.id(), actor, t.target());
                }
                case Disband _ -> GroupRepository.delete(c, group.id());
            }
            return group.id();
        }).thenApply(group -> new Confirmed(pending, group));
    }

    public Confirmation cancel(UUID actor) {
        return confirmations.remove(actor).orElseThrow(() -> new UserException("Nothing to cancel."));
    }

    // --- admin recovery (/ic group ...), not regular group rights ---

    public CompletableFuture<Group> adminSetLeader(long groupId, UUID target) {
        return mutate(c -> {
            Group group = GroupRepository.load(c, groupId).orElseThrow(() -> new UserException("That group no longer exists."));
            require(group.role(target) != null, "The new leader must be a member of the group.");
            require(!group.leader().equals(target), "That player already leads the group.");
            GroupRepository.setLeader(c, groupId, group.leader(), target);
            return groupId;
        });
    }

    public CompletableFuture<Group> adminDisband(long groupId) {
        return mutate(c -> {
            require(GroupRepository.load(c, groupId).isPresent(), "That group no longer exists.");
            GroupRepository.delete(c, groupId);
            return groupId;
        });
    }

    /** Fresh committed state for diagnostics. */
    public CompletableFuture<Optional<Group>> inspect(long groupId) {
        return database.transaction(c -> GroupRepository.load(c, groupId));
    }

    // --- internals ---

    @FunctionalInterface
    private interface SqlStep {
        void run(Connection c) throws SQLException;
    }

    /** Runs {@code work} (returns the touched group id), reloads that group in the same commit, then applies it. */
    private CompletableFuture<Group> mutate(Database.SqlWork<Long> work) {
        record Change(long id, Group fresh) {
        }
        return database.transaction(c -> {
            long id = work.run(c);
            return new Change(id, GroupRepository.load(c, id).orElse(null));
        }).thenApplyAsync(change -> apply(change.id(), change.fresh()), mainThread);
    }

    private CompletableFuture<Group> onMember(UUID actor, UUID target, Action action, String denied, SqlStep step) {
        return mutate(c -> {
            Group group = groupOf(c, actor);
            GroupRole targetRole = group.role(target);
            require(targetRole != null, "That player is not in your group.");
            require(GroupPolicy.allows(group.role(actor), action, targetRole), denied);
            step.run(c);
            return group.id();
        });
    }

    private CompletableFuture<Group> leaderEdit(UUID actor, Action action, Database.SqlWork<String> value, String column) {
        return mutate(c -> {
            Group group = groupOf(c, actor);
            require(GroupPolicy.allows(group.role(actor), action, null), "Only the leader can change name, tag or colour.");
            String newValue = value.run(c);
            if (!column.equals("color")) {
                requireFree(c, column, newValue, group.id());
            }
            GroupRepository.set(c, group.id(), column, newValue);
            return group.id();
        });
    }

    /** Replaces the cached group (null = deleted) and reports affected members. Returns fresh, or the old one if deleted. */
    private Group apply(long id, Group fresh) {
        Group old = fresh == null ? groups.remove(id) : groups.put(id, fresh);
        if (old != null) {
            for (UUID member : old.members().keySet()) {
                if (fresh == null || !fresh.members().containsKey(member)) {
                    groupOfMember.remove(member);
                    membershipChanged.accept(member, null);
                }
            }
        }
        if (fresh != null) {
            for (UUID member : fresh.members().keySet()) {
                groupOfMember.put(member, id);
                if (old == null || !old.members().containsKey(member) || !old.looksLike(fresh)) {
                    membershipChanged.accept(member, fresh);
                }
            }
        } else {
            invites.removeIf((k, v) -> k.groupId() == id);
        }
        return fresh != null ? fresh : old;
    }

    private InviteKey inviteFrom(UUID invitee, String groupName) {
        return invitesFor(invitee).stream()
                .filter(g -> g.name().equalsIgnoreCase(groupName))
                .map(g -> new InviteKey(invitee, g.id()))
                .findFirst()
                .orElseThrow(() -> new UserException("You have no pending invite from group " + groupName + "."));
    }

    private static Group groupOf(Connection c, UUID player) throws SQLException {
        return GroupRepository.ofMember(c, player).orElseThrow(() -> new UserException("You are not in a group."));
    }

    private static void validate(String name, String tag) {
        requireMatches(NAME, name, "Group names are 1-16 characters: letters, digits, _ or -.");
        requireMatches(TAG, tag, "Tags are 1-5 characters: letters, digits, _ or -.");
    }

    private static void requireMatches(Pattern pattern, String value, String message) {
        require(pattern.matcher(value).matches(), message);
    }

    private static void requireFree(Connection c, String column, String value, long exceptId) throws SQLException {
        require(!GroupRepository.taken(c, column, value, exceptId), "A group with that " + column + " already exists.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new UserException(message);
        }
    }
}
