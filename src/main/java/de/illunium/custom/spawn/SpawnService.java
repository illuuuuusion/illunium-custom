package de.illunium.custom.spawn;

import de.illunium.custom.core.ExpiringMap;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Rules of {@code /spawn} (Patch v1.2), without Bukkit calls. Per player: IDLE → WARMING_UP (10 s, cancelled by
 * movement, damage, PvP, teleport, death, quit) → COMPLETING (inside the teleport call, not cancellable) → COOLDOWN
 * (60 min, only after a successful teleport) or back to IDLE. A PvP hit tags both players for 30 s, which blocks
 * starting a warmup. Warmups and tags are volatile and use a monotonic clock; cooldowns are epoch based so they can be
 * persisted. Main thread only.
 */
final class SpawnService {

    static final Duration WARMUP = Duration.ofSeconds(10);
    static final Duration COOLDOWN = Duration.ofMinutes(60);
    static final Duration COMBAT_TAG = Duration.ofSeconds(30);
    // ponytail: guessed rounding-noise threshold (0.001 blocks), not yet calibrated with real clients.
    static final double MOVE_TOLERANCE_SQUARED = 1.0E-6;

    enum State { IDLE, WARMING_UP, COMPLETING, COOLDOWN }

    /** @param due the 10 s are over and the teleport was requested; still cancellable until it runs */
    private record Warmup(Location start, Instant started, boolean due) {
    }

    private final InstantSource monotonic;
    private final InstantSource wall;
    private final Map<UUID, Warmup> warmups = new HashMap<>();
    private final Set<UUID> completing = new HashSet<>();
    /** Value: when the tag ends, for the remaining time. */
    private final ExpiringMap<UUID, Instant> combat;
    private final Map<UUID, Instant> cooldowns = new HashMap<>();

    SpawnService(InstantSource monotonic, InstantSource wall) {
        this.monotonic = monotonic;
        this.wall = wall;
        this.combat = new ExpiringMap<>(monotonic, COMBAT_TAG);
    }

    State state(UUID player) {
        if (completing.contains(player)) {
            return State.COMPLETING;
        }
        if (warmups.containsKey(player)) {
            return State.WARMING_UP;
        }
        return cooldownLeft(player).isPositive() ? State.COOLDOWN : State.IDLE;
    }

    /** Why {@code player} can't start {@code /spawn} now, or null if they can. */
    String startProblem(UUID player) {
        if (warmups.containsKey(player) || completing.contains(player)) {
            return "Your teleport to spawn is already running (" + seconds(warmupLeft(player)) + " s left).";
        }
        Duration cooldown = cooldownLeft(player);
        if (cooldown.isPositive()) {
            long s = seconds(cooldown);
            return "/spawn is available again in " + s / 60 + " min " + s % 60 + " s.";
        }
        Duration tag = combatLeft(player);
        if (tag.isPositive()) {
            return "You are in combat: /spawn is locked for " + seconds(tag) + " s.";
        }
        return null;
    }

    /** Starts the warmup at {@code start}; check {@link #startProblem} first, in the same tick. */
    void start(UUID player, Location start) {
        warmups.put(player, new Warmup(start.clone(), monotonic.instant(), false));
    }

    Duration warmupLeft(UUID player) {
        Warmup warmup = warmups.get(player);
        if (warmup == null) {
            return Duration.ZERO;
        }
        Duration left = WARMUP.minus(Duration.between(warmup.started(), monotonic.instant()));
        return left.isNegative() ? Duration.ZERO : left;
    }

    Set<UUID> warmingUp() {
        return Set.copyOf(warmups.keySet());
    }

    /** Warmups whose 10 s just ran out. Each is returned once; it stays cancellable until {@link #beginTeleport}. */
    List<UUID> due() {
        List<UUID> due = new ArrayList<>();
        warmups.replaceAll((player, warmup) -> {
            if (warmup.due() || warmupLeft(player).isPositive()) {
                return warmup;
            }
            due.add(player);
            return new Warmup(warmup.start(), warmup.started(), true);
        });
        return due;
    }

    /** Ends a warmup without cooldown. Idempotent; returns whether one was running. COMPLETING is not affected. */
    boolean cancel(UUID player) {
        return warmups.remove(player) != null;
    }

    /** Whether a warming-up {@code player} has left the spot the warmup started on. */
    boolean moved(UUID player, Location to) {
        Warmup warmup = warmups.get(player);
        return warmup != null && moved(warmup.start(), to);
    }

    /** Position change beyond rounding noise; head rotation alone is no movement. */
    static boolean moved(Location from, Location to) {
        if (!Objects.equals(from.getWorld(), to.getWorld())) {
            return true;
        }
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        return dx * dx + dy * dy + dz * dz > MOVE_TOLERANCE_SQUARED;
    }

    /** WARMING_UP (due) → COMPLETING. False if the warmup was cancelled meanwhile: then nothing may teleport. */
    boolean beginTeleport(UUID player) {
        Warmup warmup = warmups.get(player);
        if (warmup == null || !warmup.due()) {
            return false;
        }
        warmups.remove(player);
        completing.add(player);
        return true;
    }

    /** Leaves COMPLETING. A successful teleport starts the cooldown and returns its end (to persist), else null. */
    Instant finishTeleport(UUID player, boolean success) {
        if (!completing.remove(player) || !success) {
            return null;
        }
        Instant until = wall.instant().plus(COOLDOWN);
        cooldowns.put(player, until);
        return until;
    }

    /** Sets or renews the 30 s PvP tag of {@code player} from now. */
    void tag(UUID player) {
        combat.put(player, monotonic.instant().plus(COMBAT_TAG));
    }

    Duration combatLeft(UUID player) {
        return combat.get(player).map(end -> Duration.between(monotonic.instant(), end)).orElse(Duration.ZERO);
    }

    Duration cooldownLeft(UUID player) {
        Instant until = cooldowns.get(player);
        if (until == null) {
            return Duration.ZERO;
        }
        Instant now = wall.instant();
        if (!now.isBefore(until)) {
            cooldowns.remove(player);
            return Duration.ZERO;
        }
        return Duration.between(now, until);
    }

    void loadCooldown(UUID player, Instant until) {
        cooldowns.merge(player, until, (a, b) -> a.isAfter(b) ? a : b);
    }

    Map<UUID, Instant> activeCooldowns() {
        Instant now = wall.instant();
        cooldowns.values().removeIf(until -> !now.isBefore(until));
        return Map.copyOf(cooldowns);
    }

    /** Quit: the warmup and the volatile combat tag go, the cooldown stays. */
    void forget(UUID player) {
        warmups.remove(player);
        combat.remove(player);
    }

    /** Whole seconds, rounded up so a running lock never shows 0. */
    static long seconds(Duration duration) {
        return duration.plusNanos(999_999_999).getSeconds();
    }
}
