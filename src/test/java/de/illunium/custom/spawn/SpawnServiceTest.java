package de.illunium.custom.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.illunium.custom.TestMainThread;
import de.illunium.custom.spawn.SpawnService.State;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class SpawnServiceTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final Location HERE = new Location(null, 10.5, 64, -3.5, 90, 0);

    private final TestMainThread time = new TestMainThread();
    private final SpawnService spawns = new SpawnService(time.clock(), time.clock());

    @Test
    void teleportIsDueAfterExactlyTenSecondsAndThenCoolsDownForAnHour() {
        assertNull(spawns.startProblem(A));
        spawns.start(A, HERE);
        assertEquals(State.WARMING_UP, spawns.state(A));

        time.advance(Duration.ofMillis(9_999));
        assertTrue(spawns.due().isEmpty());
        assertEquals(1, SpawnService.seconds(spawns.warmupLeft(A)));
        time.advance(Duration.ofMillis(1));
        assertEquals(List.of(A), spawns.due());
        assertTrue(spawns.due().isEmpty(), "a due warmup is returned once");

        assertTrue(spawns.beginTeleport(A));
        assertEquals(State.COMPLETING, spawns.state(A));
        assertFalse(spawns.cancel(A), "our own teleport event must not cancel the completion");
        assertNotNull(spawns.finishTeleport(A, true));
        assertEquals(State.COOLDOWN, spawns.state(A));

        time.advance(Duration.ofMinutes(17).plusSeconds(41).plusMillis(500));
        assertEquals("/spawn is available again in 42 min 19 s.", spawns.startProblem(A));
        time.advance(Duration.ofMinutes(42).plusSeconds(18).plusMillis(499));
        assertEquals(State.COOLDOWN, spawns.state(A));
        time.advance(Duration.ofMillis(1));
        assertEquals(State.IDLE, spawns.state(A));
        assertNull(spawns.startProblem(A));
    }

    @Test
    void cancelIsIdempotentAndNeverCostsTheCooldown() {
        spawns.start(A, HERE);
        time.advance(Duration.ofSeconds(9));
        assertTrue(spawns.cancel(A));
        assertFalse(spawns.cancel(A));
        assertEquals(State.IDLE, spawns.state(A));
        assertNull(spawns.startProblem(A));

        spawns.start(A, HERE);
        time.advance(Duration.ofSeconds(9));
        assertEquals(1, SpawnService.seconds(spawns.warmupLeft(A)), "a new /spawn starts from 10 s again");
    }

    @Test
    void secondCommandDuringWarmupNeitherRestartsNorShortens() {
        spawns.start(A, HERE);
        time.advance(Duration.ofSeconds(4));
        assertEquals("Your teleport to spawn is already running (6 s left).", spawns.startProblem(A));
        assertEquals(Duration.ofSeconds(6), spawns.warmupLeft(A));
    }

    @Test
    void cancelWhileTheTeleportIsRequestedWins() {
        spawns.start(A, HERE);
        time.advance(Duration.ofSeconds(10));
        assertEquals(List.of(A), spawns.due());
        assertTrue(spawns.cancel(A), "movement or damage while the spawn chunk loads");
        assertFalse(spawns.beginTeleport(A));
        assertNull(spawns.finishTeleport(A, true));
        assertEquals(State.IDLE, spawns.state(A));
    }

    @Test
    void failedTeleportStartsNoCooldown() {
        spawns.start(A, HERE);
        time.advance(Duration.ofSeconds(10));
        spawns.due();
        assertTrue(spawns.beginTeleport(A));
        assertNull(spawns.finishTeleport(A, false));
        assertEquals(State.IDLE, spawns.state(A));
    }

    @Test
    void combatTagLastsThirtySecondsAndIsRenewedByEveryHit() {
        spawns.tag(A);
        time.advance(Duration.ofMillis(29_999));
        assertEquals("You are in combat: /spawn is locked for 1 s.", spawns.startProblem(A));
        time.advance(Duration.ofMillis(1));
        assertNull(spawns.startProblem(A));

        spawns.tag(A);
        time.advance(Duration.ofSeconds(20));
        spawns.tag(A);
        time.advance(Duration.ofSeconds(20));
        assertEquals(Duration.ofSeconds(10), spawns.combatLeft(A), "renewed: 30 s from the second hit");
        assertNull(spawns.startProblem(B), "only tagged players are locked");
    }

    @Test
    void quitDropsWarmupAndTagButKeepsCooldown() {
        spawns.tag(A);
        spawns.start(A, HERE);
        spawns.forget(A);
        assertEquals(State.IDLE, spawns.state(A));
        assertEquals(Duration.ZERO, spawns.combatLeft(A));

        spawns.start(B, HERE);
        time.advance(Duration.ofSeconds(10));
        spawns.due();
        spawns.beginTeleport(B);
        spawns.finishTeleport(B, true);
        spawns.forget(B);
        assertEquals(State.COOLDOWN, spawns.state(B));
    }

    @Test
    void loadedCooldownsCountAndExpiredOnesAreNotPersistedAgain() {
        spawns.loadCooldown(A, time.clock().instant().plus(Duration.ofMinutes(5)));
        spawns.loadCooldown(B, time.clock().instant().minusMillis(1));
        assertEquals(Duration.ofMinutes(5), spawns.cooldownLeft(A));
        assertEquals(State.IDLE, spawns.state(B));
        assertEquals(List.of(A), List.copyOf(spawns.activeCooldowns().keySet()));
    }

    @Test
    void onlyPositionChangesCountAsMovement() {
        assertFalse(SpawnService.moved(HERE, new Location(null, 10.5, 64, -3.5, -45, 30)), "head rotation");
        assertFalse(SpawnService.moved(HERE, new Location(null, 10.5 + 1e-4, 64, -3.5)), "rounding noise");
        assertTrue(SpawnService.moved(HERE, new Location(null, 10.5, 64, -3.49)), "z");
        assertTrue(SpawnService.moved(HERE, new Location(null, 10.6, 64, -3.5)), "x");
        assertTrue(SpawnService.moved(HERE, new Location(null, 10.5, 64.42, -3.5)), "jump");

        spawns.start(A, HERE);
        assertFalse(spawns.moved(A, HERE));
        assertTrue(spawns.moved(A, new Location(null, 11, 64, -3.5)));
        assertFalse(spawns.moved(B, new Location(null, 11, 64, -3.5)), "no warmup, nothing to cancel");
    }
}
