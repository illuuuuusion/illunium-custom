package de.illunium.custom.core.module;

import de.illunium.custom.appearance.AppearanceService;
import de.illunium.custom.core.command.AdminCommand;
import de.illunium.custom.core.command.Chat;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.core.identity.PlayerIdentityService;
import de.illunium.custom.storage.Database;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared services handed to a module.
 *
 * @param settings   the module's own config section (empty if absent); validate it in {@code enable}
 * @param appearance report nametag/tablist data here instead of touching scoreboards or list names
 * @param admin      register {@code /ic <name>} subcommands here
 * @param mainThread runs tasks on the primary server thread; silently drops them once the plugin is disabled.
 *                   Use it to continue {@link Database#transaction} results with Bukkit API calls.
 */
public record ModuleContext(
        JavaPlugin plugin,
        ConfigurationSection settings,
        Database database,
        PlayerIdentityService identities,
        AppearanceService appearance,
        AdminCommand admin,
        Executor mainThread
) {

    /**
     * Finishes a command on the main thread: success goes to {@code onSuccess}, a {@link UserException} is shown
     * to the sender, anything else is logged and reported as an internal error.
     */
    public <T> void reply(CompletableFuture<T> result, CommandSender sender, Consumer<T> onSuccess) {
        result.whenCompleteAsync((value, error) -> {
            if (error == null) {
                onSuccess.accept(value);
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (cause instanceof UserException) {
                sender.sendMessage(Chat.error(cause.getMessage()));
            } else {
                plugin.getLogger().log(Level.SEVERE, "Command of " + sender.getName() + " failed", cause);
                sender.sendMessage(Chat.error("Internal error, nothing was changed. Please tell an admin."));
            }
        }, mainThread);
    }
}
