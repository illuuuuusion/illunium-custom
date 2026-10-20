package de.illunium.custom.core.module;

import java.util.List;

/** Explicit module registry, in enable order. A new feature is added here and nowhere else in core. */
public final class Modules {

    private Modules() {
    }

    public static List<FeatureModule> create() {
        return List.of();
    }
}
