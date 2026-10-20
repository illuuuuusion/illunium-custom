package de.illunium.custom.core.module;

/** A feature of the plugin. Enabled once per plugin start; there is no hot reload. */
public interface FeatureModule {

    /** Stable lowercase id; also the key under {@code modules:} and the module's config section. */
    String id();

    /** Registers commands, listeners and tasks. Throwing aborts the whole plugin start. */
    void enable(ModuleContext context) throws Exception;

    /** Releases everything the module owns. Also called after a failed or partial {@link #enable}. */
    void disable();
}
