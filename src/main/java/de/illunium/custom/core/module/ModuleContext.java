package de.illunium.custom.core.module;

import de.illunium.custom.core.identity.PlayerIdentityService;
import de.illunium.custom.storage.Database;
import java.util.concurrent.Executor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared services handed to a module.
 *
 * @param settings   the module's own config section (empty if absent); validate it in {@code enable}
 * @param mainThread runs tasks on the primary server thread; silently drops them once the plugin is disabled.
 *                   Use it to continue {@link Database#transaction} results with Bukkit API calls.
 */
public record ModuleContext(
        JavaPlugin plugin,
        ConfigurationSection settings,
        Database database,
        PlayerIdentityService identities,
        Executor mainThread
) {
}
