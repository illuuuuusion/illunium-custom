package de.illunium.custom.marriage;

import de.illunium.custom.core.ExpiringMap;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.storage.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/**
 * Partnerships (MVP §5). The marriage itself is checked again inside its transaction (and by the schema's disjoint
 * constraints), so simultaneous accepts can never marry anyone twice. Proposals and divorce confirmations are volatile.
 * Public methods are called on the main thread and either throw {@link UserException} or return a future that may
 * fail with one.
 */
public final class MarriageService {

    static final Duration PROPOSAL_TTL = Duration.ofMinutes(5);
    static final Duration DIVORCE_TTL = Duration.ofSeconds(30);

    private final Database database;
    private final Executor mainThread;
    private final BiConsumer<UUID, UUID> statusChanged;
    private final Map<UUID, UUID> partners = new HashMap<>();
    /** recipient -> proposer; at most one per recipient. */
    private final ExpiringMap<UUID, UUID> proposals;
    /** initiator -> partner at request time. */
    private final ExpiringMap<UUID, UUID> divorces;

    private MarriageService(Database database, Executor mainThread, InstantSource clock, BiConsumer<UUID, UUID> statusChanged) {
        this.database = database;
        this.mainThread = mainThread;
        this.statusChanged = statusChanged;
        this.proposals = new ExpiringMap<>(clock, PROPOSAL_TTL);
        this.divorces = new ExpiringMap<>(clock, DIVORCE_TTL);
    }

    /**
     * Loads all marriages; blocks, so call only during startup.
     *
     * @param statusChanged called on the main thread with a player and their partner (null if none) on load, marriage
     *                      and divorce
     */
    public static MarriageService load(Database database, Executor mainThread, InstantSource clock,
                                       BiConsumer<UUID, UUID> statusChanged) {
        MarriageService service = new MarriageService(database, mainThread, clock, statusChanged);
        database.transaction(c -> {
            Map<UUID, UUID> pairs = new HashMap<>();
            try (PreparedStatement s = c.prepareStatement("SELECT partner_a_uuid, partner_b_uuid FROM marriages");
                 ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    pairs.put(UUID.fromString(r.getString(1)), UUID.fromString(r.getString(2)));
                }
            }
            return pairs;
        }).join().forEach(service::married);
        return service;
    }

    public Optional<UUID> partner(UUID player) {
        return Optional.ofNullable(partners.get(player));
    }

    public void propose(UUID from, UUID to) {
        if (from.equals(to)) {
            throw new UserException("You can't marry yourself.");
        }
        if (partners.containsKey(from)) {
            throw new UserException("You are already married.");
        }
        if (partners.containsKey(to)) {
            throw new UserException("That player is already married.");
        }
        if (proposals.get(from).filter(to::equals).isPresent()) {
            throw new UserException("That player already proposed to you. Use /marry accept.");
        }
        Optional<UUID> pending = proposals.get(to);
        if (pending.isPresent()) {
            throw new UserException(pending.get().equals(from)
                    ? "You already proposed to that player." : "That player already has a pending proposal.");
        }
        proposals.put(to, from);
    }

    /** Completes with the new partner. */
    public CompletableFuture<UUID> accept(UUID actor) {
        UUID proposer = proposals.remove(actor).orElseThrow(() -> new UserException("You have no pending proposal."));
        UUID a = min(actor, proposer);
        UUID b = max(actor, proposer);
        long now = System.currentTimeMillis();
        return database.transaction(c -> {
            if (isMarried(c, a) || isMarried(c, b)) {
                throw new UserException("One of you is already married.");
            }
            try (PreparedStatement s = c.prepareStatement(
                    "INSERT INTO marriages (partner_a_uuid, partner_b_uuid, married_at) VALUES (?, ?, ?)")) {
                s.setString(1, a.toString());
                s.setString(2, b.toString());
                s.setLong(3, now);
                s.executeUpdate();
            }
            return proposer;
        }).thenApplyAsync(partner -> {
            married(a, b);
            proposals.removeIf((recipient, from) -> recipient.equals(a) || recipient.equals(b) || from.equals(a) || from.equals(b));
            return partner;
        }, mainThread);
    }

    /** Returns the proposer. */
    public UUID deny(UUID actor) {
        return proposals.remove(actor).orElseThrow(() -> new UserException("You have no pending proposal."));
    }

    /** Stores a pending divorce for {@link #confirmDivorce}; returns the partner. */
    public UUID requestDivorce(UUID actor) {
        UUID partner = partner(actor).orElseThrow(() -> new UserException("You are not married."));
        divorces.put(actor, partner);
        return partner;
    }

    /** Ends the marriage for both; completes with the former partner. */
    public CompletableFuture<UUID> confirmDivorce(UUID actor) {
        UUID partner = divorces.remove(actor)
                .orElseThrow(() -> new UserException("Nothing to confirm (requests expire after 30 seconds)."));
        UUID a = min(actor, partner);
        UUID b = max(actor, partner);
        return database.transaction(c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "DELETE FROM marriages WHERE partner_a_uuid = ? AND partner_b_uuid = ?")) {
                s.setString(1, a.toString());
                s.setString(2, b.toString());
                if (s.executeUpdate() == 0) {
                    throw new UserException("You are no longer married to that player.");
                }
            }
            return partner;
        }).thenApplyAsync(former -> {
            partners.remove(a);
            partners.remove(b);
            divorces.removeIf((initiator, p) -> initiator.equals(former));
            statusChanged.accept(a, null);
            statusChanged.accept(b, null);
            return former;
        }, mainThread);
    }

    public void cancelDivorce(UUID actor) {
        divorces.remove(actor).orElseThrow(() -> new UserException("Nothing to cancel."));
    }

    private void married(UUID a, UUID b) {
        partners.put(a, b);
        partners.put(b, a);
        statusChanged.accept(a, b);
        statusChanged.accept(b, a);
    }

    private static boolean isMarried(Connection c, UUID player) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT 1 FROM marriages WHERE partner_a_uuid = ? OR partner_b_uuid = ?")) {
            s.setString(1, player.toString());
            s.setString(2, player.toString());
            try (ResultSet r = s.executeQuery()) {
                return r.next();
            }
        }
    }

    /** Canonical order matching the schema's {@code partner_a_uuid < partner_b_uuid} on the stored strings. */
    private static UUID min(UUID x, UUID y) {
        return x.toString().compareTo(y.toString()) < 0 ? x : y;
    }

    private static UUID max(UUID x, UUID y) {
        return min(x, y).equals(x) ? y : x;
    }
}
