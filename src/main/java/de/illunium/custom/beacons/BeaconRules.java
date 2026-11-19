package de.illunium.custom.beacons;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Vanilla 26.3 beacon rules ({@code BeaconBlockEntity.updateBase/applyEffects}), without Bukkit. */
final class BeaconRules {

    static final int MAX_TIER = 4;

    private BeaconRules() {
    }

    interface BaseBlocks {
        boolean isBase(int x, int y, int z);
    }

    record Grant<T>(T type, int amplifier) {
    }

    /** Complete pyramid layers below the beacon: layer i is the (2i+1)² square at y-i; stops at the first gap. */
    static int tier(int x, int y, int z, int minY, BaseBlocks base) {
        int tier = 0;
        for (int layer = 1; layer <= MAX_TIER; layer++) {
            int ly = y - layer;
            if (ly < minY) return tier;
            for (int lx = x - layer; lx <= x + layer; lx++) {
                for (int lz = z - layer; lz <= z + layer; lz++) {
                    if (!base.isBase(lx, ly, lz)) return tier;
                }
            }
            tier = layer;
        }
        return tier;
    }

    static double range(int tier) {
        return tier * 10 + 10;
    }

    static int durationTicks(int tier) {
        return (9 + tier * 2) * 20;
    }

    /** Primary (II only at tier 4 with the same secondary) plus, at tier 4, a different secondary at level I. */
    static <T> List<Grant<T>> grants(int tier, T primary, T secondary) {
        List<Grant<T>> grants = new ArrayList<>();
        if (tier <= 0 || primary == null) return grants;
        boolean same = Objects.equals(primary, secondary);
        grants.add(new Grant<>(primary, tier >= MAX_TIER && same ? 1 : 0));
        if (tier >= MAX_TIER && !same && secondary != null) grants.add(new Grant<>(secondary, 0));
        return grants;
    }
}
