package de.illunium.custom.marriage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class MarriageModuleTest {

    @Test
    void heartsNeedTheSameWorldAndAtMostThreeBlocks() {
        World world = world();
        Location a = new Location(world, 10, 64, 10);

        assertTrue(MarriageModule.closeEnough(a, new Location(world, 12.9, 64, 10)), "2.9");
        assertTrue(MarriageModule.closeEnough(a, new Location(world, 13.0, 64, 10)), "3.0");
        assertFalse(MarriageModule.closeEnough(a, new Location(world, 13.1, 64, 10)), "3.1");
        assertTrue(MarriageModule.closeEnough(a, new Location(world, 10, 67, 10)), "3.0 vertically");
        assertFalse(MarriageModule.closeEnough(a, new Location(world, 12.2, 66.2, 10)), "diagonal 3.11");
        assertFalse(MarriageModule.closeEnough(a, new Location(world(), 10, 64, 10)), "other world, same coordinates");
    }

    private static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (proxy, method, args) -> {
            if (method.getName().equals("equals")) {
                return proxy == args[0];
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }
}
