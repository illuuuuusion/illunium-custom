package de.illunium.custom.leaderboard;

import static org.junit.jupiter.api.Assertions.*;
import de.illunium.custom.spawn.SpawnTeleportSucceeded;
import de.illunium.custom.storage.Database;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomMetricTrackerTest {
    @TempDir Path dir;
    @Test void cancelledPlacesMultiPlaceGameModesTotemsAndPeak() throws Exception {
        UUID id = UUID.randomUUID();
        Player survival = player(id, GameMode.SURVIVAL);
        try (Database db = Database.open(dir.resolve("metrics.db"))) {
            var repository = new LeaderboardRepository(db);
            var tracker = new CustomMetricTracker(repository, Logger.getAnonymousLogger());
            BlockPlaceEvent normal = place(survival, true); tracker.onPlace(normal);
            normal.setCancelled(true); tracker.onPlace(normal);
            tracker.onPlace(place(survival, false));
            tracker.onPlace(place(player(id, GameMode.CREATIVE), true));
            tracker.onPlace(place(player(id, GameMode.SPECTATOR), true));
            tracker.onPlace(new BlockMultiPlaceEvent(List.of(state(0), state(1), state(1)), null, null, survival, true, EquipmentSlot.HAND));
            tracker.onTotem(new EntityResurrectEvent(survival, null));
            var cancelled = new EntityResurrectEvent(survival, EquipmentSlot.HAND); cancelled.setCancelled(true); tracker.onTotem(cancelled);
            tracker.onTotem(new EntityResurrectEvent(survival, EquipmentSlot.OFF_HAND));
            tracker.onLevel(new PlayerLevelChangeEvent(survival, 30, 2));
            tracker.onLevel(new PlayerLevelChangeEvent(survival, 2, 4));
            UUID session = UUID.randomUUID(); tracker.onSpawn(new SpawnTeleportSucceeded(id, session)); tracker.onSpawn(new SpawnTeleportSucceeded(id, session));
            tracker.close();
            assertEquals(new LeaderboardRepository.Metrics(3, 1, 1, 30), repository.load().join().get(id));
        }
    }
    private static Player player(UUID id, GameMode mode) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p,m,a) -> switch(m.getName()) {
            case "getUniqueId" -> id; case "getGameMode" -> mode; case "getLevel" -> 12; default -> throw new UnsupportedOperationException(m.getName());
        });
    }
    private static BlockState state(int x) {
        return (BlockState) Proxy.newProxyInstance(BlockState.class.getClassLoader(), new Class<?>[]{BlockState.class}, (p,m,a) -> switch(m.getName()) {
            case "getBlock" -> null; case "getLocation" -> new Location(null, x, 0, 0); default -> throw new UnsupportedOperationException(m.getName());
        });
    }
    private static BlockPlaceEvent place(Player player, boolean canBuild) { return new BlockPlaceEvent(null, null, null, null, player, canBuild, EquipmentSlot.HAND); }
}
