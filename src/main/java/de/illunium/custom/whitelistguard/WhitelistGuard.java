package de.illunium.custom.whitelistguard;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

/**
 * Keeps the native whitelist switched on and enforced, and puts verified staff back on it. Never removes or rewrites
 * other entries and keeps no list of its own; {@code whitelist.json} stays the only source of truth. Main thread only.
 */
final class WhitelistGuard {

    private final Server server;
    private final Logger logger;
    private final List<StaffEntry> staff;
    /** Staff whose UUID/name pair Mojang confirmed this run; only these are ever added. */
    private final Set<UUID> verified = new HashSet<>();

    WhitelistGuard(Server server, Logger logger, List<StaffEntry> staff) {
        this.server = server;
        this.logger = logger;
        this.staff = staff;
    }

    /** Result of the profile lookup for {@code entry}; {@code resolvedName} is null if Mojang did not know the UUID. */
    void verified(StaffEntry entry, UUID resolvedId, String resolvedName) {
        if (resolvedName == null || !entry.uuid().equals(resolvedId)) {
            logger.severe("Staff " + entry.name() + " (" + entry.uuid() + ") could not be resolved (unknown UUID or Mojang"
                    + " unreachable). NOT protected until the next restart.");
        } else if (!resolvedName.equalsIgnoreCase(entry.name())) {
            logger.severe("Staff UUID " + entry.uuid() + " belongs to '" + resolvedName + "', not '" + entry.name()
                    + "'. NOT protected; fix whitelistguard.staff and restart.");
        } else {
            verified.add(entry.uuid());
            logger.info("Staff " + entry.name() + " (" + entry.uuid() + ") verified and protected");
            check();
        }
    }

    /** One consistency pass; cheap (no network), so it runs on every interval. */
    void check() {
        if (!server.hasWhitelist()) {
            logger.warning("Native whitelist was off; turning it on (white-list=true)");
            server.setWhitelist(true);
            if (!server.hasWhitelist()) {
                logger.severe("Could not turn the native whitelist on");
            }
        }
        if (!server.isWhitelistEnforced()) {
            logger.warning("Whitelist enforcement was off; turning it on (enforce-whitelist=true)");
            server.setWhitelistEnforced(true);
            if (!server.isWhitelistEnforced()) {
                logger.severe("Could not turn whitelist enforcement on");
            }
        }
        for (StaffEntry entry : staff) {
            if (!verified.contains(entry.uuid())) {
                continue;
            }
            OfflinePlayer player = server.getOfflinePlayer(entry.uuid());
            if (!player.isWhitelisted()) {
                player.setWhitelisted(true);
                logger.warning("Protected staff " + entry.name() + " (" + entry.uuid() + ") was missing from the native"
                        + " whitelist and has been added back");
            }
        }
    }
}
