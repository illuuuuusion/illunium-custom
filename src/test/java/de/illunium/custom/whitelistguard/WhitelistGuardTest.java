package de.illunium.custom.whitelistguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;

class WhitelistGuardTest {

    private static final UUID ADMIN = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID MOD = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");

    /** Native whitelist state of the fake server. */
    private boolean whitelist;
    private boolean enforced;
    private final Set<UUID> listed = new HashSet<>();
    private final List<LogRecord> logs = new ArrayList<>();

    @Test
    void parsesOnlyVerifiableStaffEntries() {
        assertEquals(List.of(new StaffEntry(ADMIN, "Notch")),
                StaffEntry.parse(List.of(Map.of("uuid", ADMIN.toString(), "name", "Notch"))));
        assertEquals(List.of(), StaffEntry.parse(List.of()));

        String offlineUuid = UUID.nameUUIDFromBytes("OfflinePlayer:Notch".getBytes()).toString();
        for (List<?> invalid : List.of(
                List.of("Notch"),
                List.of(Map.of("name", "Notch")),
                List.of(Map.of("uuid", "069a79f444e94726a5befca90e38aaf5", "name", "Notch")),
                List.of(Map.of("uuid", offlineUuid, "name", "Notch")),
                List.of(Map.of("uuid", ADMIN.toString(), "name", "Not ch")),
                List.of(Map.of("uuid", ADMIN.toString(), "name", "a".repeat(17))),
                List.of(Map.of("uuid", ADMIN.toString(), "name", "Notch"), Map.of("uuid", ADMIN.toString(), "name", "Other")))) {
            assertThrows(IllegalArgumentException.class, () -> StaffEntry.parse(invalid), invalid.toString());
        }
    }

    @Test
    void turnsWhitelistAndEnforcementBackOn() {
        WhitelistGuard guard = guard(List.of());
        guard.check();
        assertTrue(whitelist);
        assertTrue(enforced);
        assertEquals(2, count(Level.WARNING));

        guard.check();
        assertEquals(2, count(Level.WARNING), "no log spam while everything is fine");
    }

    @Test
    void restoresOnlyVerifiedStaffAndNeverRemovesEntries() {
        UUID regular = UUID.randomUUID();
        listed.add(regular);
        StaffEntry admin = new StaffEntry(ADMIN, "Notch");
        StaffEntry mod = new StaffEntry(MOD, "Mod");
        WhitelistGuard guard = guard(List.of(admin, mod));

        guard.check();
        assertEquals(Set.of(regular), listed, "unverified staff must not be added");

        guard.verified(admin, ADMIN, "notch");
        assertEquals(Set.of(regular, ADMIN), listed);

        listed.remove(ADMIN); // accidental /whitelist remove
        guard.check();
        assertEquals(Set.of(regular, ADMIN), listed);
    }

    @Test
    void unresolvableOrMismatchedStaffIsNeverAdded() {
        StaffEntry admin = new StaffEntry(ADMIN, "Notch");
        WhitelistGuard guard = guard(List.of(admin));
        guard.verified(admin, ADMIN, null);
        guard.verified(admin, ADMIN, "SomeoneElse");
        guard.verified(admin, MOD, "Notch");
        guard.check();
        assertFalse(listed.contains(ADMIN));
        assertEquals(3, count(Level.SEVERE));
    }

    private long count(Level level) {
        return logs.stream().filter(r -> r.getLevel() == level).count();
    }

    private WhitelistGuard guard(List<StaffEntry> staff) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logs.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        Server server = (Server) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasWhitelist" -> whitelist;
                    case "setWhitelist" -> {
                        whitelist = (boolean) args[0];
                        yield null;
                    }
                    case "isWhitelistEnforced" -> enforced;
                    case "setWhitelistEnforced" -> {
                        enforced = (boolean) args[0];
                        yield null;
                    }
                    case "getOfflinePlayer" -> offlinePlayer((UUID) args[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new WhitelistGuard(server, logger, staff);
    }

    private OfflinePlayer offlinePlayer(UUID uuid) {
        return (OfflinePlayer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{OfflinePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isWhitelisted" -> listed.contains(uuid);
                    case "setWhitelisted" -> (boolean) args[0] ? listed.add(uuid) : listed.remove(uuid);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
