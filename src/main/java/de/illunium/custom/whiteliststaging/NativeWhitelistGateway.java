package de.illunium.custom.whiteliststaging;

import de.illunium.custom.core.command.UserException;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

/** Main thread only. Uses the vanilla command with a verified, already cached name; never the UUID-only setter. */
final class NativeWhitelistGateway {
    private final Server server;
    NativeWhitelistGateway(Server server) { this.server = server; }
    boolean contains(VerifiedProfile profile) {
        return server.getWhitelistedPlayers().stream().anyMatch(p -> p.getUniqueId().equals(profile.uuid()));
    }
    boolean verified(VerifiedProfile profile) {
        return server.getWhitelistedPlayers().stream().anyMatch(p -> p.getUniqueId().equals(profile.uuid())
                && p.getName() != null && !p.getName().isBlank() && p.getName().equalsIgnoreCase(profile.name()));
    }
    void add(VerifiedProfile profile) {
        if (!server.hasWhitelist() || !server.isWhitelistEnforced()) throw new UserException("Native Whitelist und Enforcement müssen aktiv sein.");
        OfflinePlayer cached = server.getOfflinePlayerIfCached(profile.name());
        if (cached == null || !cached.getUniqueId().equals(profile.uuid())) throw new UserException("Verifiziertes Profil fehlt im nativen Namenscache; bitte retry.");
        if (!contains(profile)) server.dispatchCommand(server.getConsoleSender(), "minecraft:whitelist add " + profile.name());
        if (!verified(profile)) throw new UserException("Native Whitelist enthält UUID/Name nicht korrekt; keine Erfolgsbestätigung.");
    }
}
