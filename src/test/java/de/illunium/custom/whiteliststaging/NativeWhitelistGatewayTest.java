package de.illunium.custom.whiteliststaging;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class NativeWhitelistGatewayTest {
    private final VerifiedProfile profile = new VerifiedProfile(UUID.randomUUID(), "Alice");
    private final Map<UUID, OfflinePlayer> whitelist = new HashMap<>();
    private int commands;
    private boolean cached = true, enforce = true, broken;
    @Test void onlyConsoleAndVanillaOpEvenWithAdminPermission() {
        assertTrue(WhitelistStagingModule.allowed(sender(ConsoleCommandSender.class, false)));
        assertTrue(WhitelistStagingModule.allowed(sender(Player.class, true)));
        assertFalse(WhitelistStagingModule.allowed(sender(Player.class, false)));
        assertFalse(WhitelistStagingModule.allowed(sender(CommandSender.class, true)));
    }
    @Test void nativeCommandIsIdempotentAndDoesNotChangeUnrelatedEntries() {
        UUID staff = UUID.randomUUID(); whitelist.put(staff, offline(staff, "Staff"));
        var g = new NativeWhitelistGateway(server()); g.add(profile); g.add(profile);
        assertEquals(1, commands); assertTrue(g.verified(profile)); assertEquals(2, whitelist.size());
        whitelist.remove(profile.uuid()); assertFalse(g.contains(profile), "native removal stays authoritative");
    }
    @Test void noBlindWriteOrSuccessOnEmptyNameWrongCacheOrDisabledWhitelist() {
        var g = new NativeWhitelistGateway(server());
        cached = false; assertThrows(RuntimeException.class, () -> g.add(profile)); assertEquals(0, commands);
        cached = true; enforce = false; assertThrows(RuntimeException.class, () -> g.add(profile)); assertEquals(0, commands);
        enforce = true; broken = true; assertThrows(RuntimeException.class, () -> g.add(profile)); assertFalse(g.verified(profile));
    }
    private Server server() {
        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class}, (p,m,a) -> switch(m.getName()) {
            case "getWhitelistedPlayers" -> Set.copyOf(whitelist.values());
            case "hasWhitelist" -> true; case "isWhitelistEnforced" -> enforce;
            case "getOfflinePlayerIfCached" -> cached ? offline(profile.uuid(), profile.name()) : null;
            case "getConsoleSender" -> sender(ConsoleCommandSender.class, false);
            case "dispatchCommand" -> { assertEquals("minecraft:whitelist add Alice", a[1]); commands++; whitelist.put(profile.uuid(), offline(profile.uuid(), broken ? "" : profile.name())); yield true; }
            default -> throw new UnsupportedOperationException(m.getName());
        });
    }
    private static OfflinePlayer offline(UUID id, String name) {
        return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(), new Class<?>[]{OfflinePlayer.class}, (p,m,a) -> switch(m.getName()) {
            case "getUniqueId" -> id; case "getName" -> name; case "hashCode" -> id.hashCode(); case "equals" -> p == a[0]; default -> throw new UnsupportedOperationException(m.getName());
        });
    }
    private static <T> T sender(Class<T> type, boolean op) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a) -> switch(m.getName()) {
            case "isOp" -> op; case "hasPermission" -> true; default -> throw new UnsupportedOperationException(m.getName());
        }));
    }
}
