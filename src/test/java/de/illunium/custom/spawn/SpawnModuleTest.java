package de.illunium.custom.spawn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class SpawnModuleTest {

    private static final Block AIR = block(Material.AIR, true, false);
    private static final Block STONE = block(Material.STONE, false, false);
    private static final Block WATER = block(Material.WATER, true, true);
    private static final Block LAVA = block(Material.LAVA, true, true);
    private static final Block FIRE = block(Material.FIRE, true, false);

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
