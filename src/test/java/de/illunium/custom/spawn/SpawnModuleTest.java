package de.illunium.custom.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.illunium.custom.storage.Database;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpawnModuleTest {

    private static final Block AIR = block(Material.AIR, true, false);
    private static final Block STONE = block(Material.STONE, false, false);
    private static final Block WATER = block(Material.WATER, true, true);
    private static final Block LAVA = block(Material.LAVA, true, true);
    private static final Block FIRE = block(Material.FIRE, true, false);
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    @TempDir
    Path dir;

    @Test
    void needsSolidGroundAndTwoFreeBlocks() {
        assertTrue(SpawnModule.standable(STONE, AIR, AIR));

        assertFalse(SpawnModule.standable(AIR, AIR, AIR), "nothing to stand on");
        assertFalse(SpawnModule.standable(LAVA, AIR, AIR), "lava below");
        assertFalse(SpawnModule.standable(STONE, STONE, AIR), "feet in a block");
        assertFalse(SpawnModule.standable(STONE, AIR, STONE), "head in a block");
        assertFalse(SpawnModule.standable(STONE, WATER, AIR), "in water");
        assertFalse(SpawnModule.standable(STONE, LAVA, AIR), "in lava");
        assertFalse(SpawnModule.standable(STONE, FIRE, AIR), "in fire");
    }

    @Test
    void cooldownsSurviveRestartAndExpiredRowsAreDropped() throws Exception {
        Path file = dir.resolve("data.db");
        try (Database db = Database.open(file)) {
            db.transaction(c -> SpawnModule.saveCooldowns(c, Map.of(A, NOW.plusSeconds(60), B, NOW))).join();
            db.transaction(c -> SpawnModule.saveCooldowns(c, Map.of(A, NOW.plusSeconds(3600)))).join();
        }
        try (Database db = Database.open(file)) {
            long now = NOW.toEpochMilli();
            assertEquals(Map.of(A, NOW.plusSeconds(3600)), db.transaction(c -> SpawnModule.loadCooldowns(c, now)).join(),
                    "A updated in place, B expired exactly now");
            assertEquals(Map.of(), db.transaction(c -> SpawnModule.loadCooldowns(c, now + 3_600_000)).join());
        }
    }

    private static Block block(Material type, boolean passable, boolean liquid) {
        Map<String, Object> answers = Map.of("getType", type, "isPassable", passable, "isLiquid", liquid);
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class}, (proxy, method, args) -> {
            if (!answers.containsKey(method.getName())) {
                throw new UnsupportedOperationException(method.getName());
            }
            return answers.get(method.getName());
        });
    }
}
