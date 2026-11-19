package de.illunium.custom.beacons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BeaconRulesTest {

    private static final int X = 100, Y = 64, Z = -20;

    @Test
    void tierCountsCompleteLayersFromTheTop() {
        for (int tier = 0; tier <= 4; tier++) assertEquals(tier, tier(pyramid(tier), -64), "full pyramid " + tier);
        Set<String> gap = pyramid(4);
        gap.remove(key(X + 2, Y - 2, Z - 2)); // corner of layer 2
        assertEquals(1, tier(gap, -64), "a gap stops the count, lower layers do not matter");
        Set<String> top = pyramid(4);
        top.remove(key(X, Y - 1, Z)); // directly below the beacon
        assertEquals(0, tier(top, -64));
        assertEquals(2, tier(pyramid(4), Y - 2), "layers below the world floor do not count");
        Set<String> extra = pyramid(1);
        extra.add(key(X + 5, Y - 2, Z));
        assertEquals(1, tier(extra, -64));
    }

    @Test
    void vanillaRangeDurationAndGrants() {
        assertEquals(20, BeaconRules.range(1));
        assertEquals(50, BeaconRules.range(4));
        assertEquals(220, BeaconRules.durationTicks(1));
        assertEquals(340, BeaconRules.durationTicks(4));
        assertEquals(List.of(), BeaconRules.grants(0, "speed", null));
        assertEquals(List.of(), BeaconRules.grants(4, null, "regeneration"));
        for (int tier = 1; tier <= 3; tier++) {
            assertEquals(List.of(new BeaconRules.Grant<>("haste", 0)), BeaconRules.grants(tier, "haste", "haste"), "no II below tier 4");
            assertEquals(List.of(new BeaconRules.Grant<>("haste", 0)), BeaconRules.grants(tier, "haste", "regeneration"), "no secondary below tier 4");
        }
        assertEquals(List.of(new BeaconRules.Grant<>("haste", 1)), BeaconRules.grants(4, "haste", "haste"));
        assertEquals(List.of(new BeaconRules.Grant<>("speed", 0), new BeaconRules.Grant<>("regeneration", 0)),
                BeaconRules.grants(4, "speed", "regeneration"));
        assertEquals(List.of(new BeaconRules.Grant<>("speed", 0)), BeaconRules.grants(4, "speed", null));
    }

    private static int tier(Set<String> base, int minY) {
        return BeaconRules.tier(X, Y, Z, minY, (x, y, z) -> base.contains(key(x, y, z)));
    }

    private static Set<String> pyramid(int tiers) {
        Set<String> blocks = new HashSet<>();
        for (int layer = 1; layer <= tiers; layer++)
            for (int x = X - layer; x <= X + layer; x++)
                for (int z = Z - layer; z <= Z + layer; z++) blocks.add(key(x, Y - layer, z));
        return blocks;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
