package de.illunium.custom.sit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class SitModuleTest {

    @Test
    void sittingNeedsSolidGroundAndAnIdlePlayer() {
        assertNull(SitModule.problem(player(Map.of(), true)));
        assertNull(SitModule.problem(player(Map.of("getGameMode", GameMode.CREATIVE), true)), "creative on the ground may sit");

        assertEquals("You need to stand on solid ground.", SitModule.problem(player(Map.of(), false)));
        assertEquals("Spectators can't sit.", SitModule.problem(player(Map.of("getGameMode", GameMode.SPECTATOR), true)));
        assertEquals("You are already riding something.", SitModule.problem(player(Map.of("isInsideVehicle", true), true)));
        for (String state : new String[]{"isFlying", "isGliding", "isRiptiding"}) {
            assertEquals("You can't sit while flying.", SitModule.problem(player(Map.of(state, true), true)), state);
        }
        for (String state : new String[]{"isSwimming", "isInWater", "isInLava"}) {
            assertEquals("You can't sit in liquids.", SitModule.problem(player(Map.of(state, true), true)), state);
        }
        assertEquals("You can't sit in bed.", SitModule.problem(player(Map.of("isSleeping", true), true)));
    }

    private record Keys(boolean isForward, boolean isBackward, boolean isLeft, boolean isRight, boolean isJump,
                        boolean isSneak, boolean isSprint) implements Input {
    }

    @Test
    void onlyANewlyPressedKeyStandsUp() {
        Keys none = new Keys(false, false, false, false, false, false, false);
        Keys[] each = {
                new Keys(true, false, false, false, false, false, false),
                new Keys(false, true, false, false, false, false, false),
                new Keys(false, false, true, false, false, false, false),
                new Keys(false, false, false, true, false, false, false),
                new Keys(false, false, false, false, true, false, false),
                new Keys(false, false, false, false, false, true, false),
                new Keys(false, false, false, false, false, false, true),
        };
        for (Keys key : each) {
            assertTrue(SitModule.pressedMoveKey(none, key), key.toString());
            assertFalse(SitModule.pressedMoveKey(key, key), "held since sitting down: " + key);
            assertFalse(SitModule.pressedMoveKey(key, none), "releasing: " + key);
        }
        assertFalse(SitModule.pressedMoveKey(none, none), "mouse movement sends no key change");
        assertTrue(SitModule.pressedMoveKey(each[0], new Keys(true, false, false, false, true, false, false)), "W held, Space pressed");
    }

    /** A survival player standing at y=64.0; {@code solidBelow} decides the block at y=63. */
    private static Player player(Map<String, Object> overrides, boolean solidBelow) {
        Block block = fake(Block.class, Map.of("isPassable", !solidBelow, "isLiquid", false));
        World world = fake(World.class, Map.of("getBlockAt", block));
        Map<String, Object> answers = new HashMap<>(Map.of("getGameMode", GameMode.SURVIVAL));
        answers.putAll(overrides);
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> method.getName().equals("getLocation") ? new Location(world, 0.5, 64, 0.5)
                        : answers.getOrDefault(method.getName(), method.getReturnType() == boolean.class ? false : null));
    }

    private static <T> T fake(Class<T> type, Map<String, Object> answers) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (!answers.containsKey(method.getName())) {
                throw new UnsupportedOperationException(method.getName());
            }
            return answers.get(method.getName());
        }));
    }
}
