package de.illunium.custom.vanish;

import static org.junit.jupiter.api.Assertions.*;
import de.illunium.custom.core.VisibilityService;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

class VanishModuleTest {
    @Test void hiddenDefaultVisibleOverrideAndNewViewersAndQuitUseOneVisibilityService() throws Exception {
        UUID moderator = UUID.randomUUID(), survivor = UUID.randomUUID(), ordinary = UUID.randomUUID();
        List<String> hiddenPairs = new ArrayList<>();
        Player mod = player(moderator, true, hiddenPairs), admin = player(survivor, true, hiddenPairs), normal = player(ordinary, false, hiddenPairs);
        List<Player> online = List.of(mod, admin, normal);
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class}, (p,m,a) -> switch (m.getName()) {
            case "getOnlinePlayers" -> online;
            case "getPlayer" -> online.stream().filter(v -> v.getUniqueId().equals(a[0])).findFirst().orElse(null);
            default -> throw new UnsupportedOperationException(m.getName());
        });
        var visibility = new VisibilityService(server);
        var module = new VanishModule();
        field(module, "server", server); field(module, "visibility", visibility);
        field(module, "policy", new AutoVanishPolicy(true, Map.of(survivor, false, ordinary, true)));
        var hiddenJoin = new PlayerJoinEvent(mod, Component.text("joined")); module.onJoin(hiddenJoin);
        assertNull(hiddenJoin.joinMessage()); assertFalse(visibility.isPubliclyVisible(moderator));
        var visibleJoin = new PlayerJoinEvent(admin, Component.text("joined")); module.onJoin(visibleJoin);
        assertNotNull(visibleJoin.joinMessage()); assertTrue(visibility.isPubliclyVisible(survivor));
        assertTrue(hiddenPairs.contains(survivor + ":" + moderator), "visible joining OP still cannot see hidden moderator");
        var regularJoin = new PlayerJoinEvent(normal, Component.text("joined")); module.onJoin(regularJoin);
        assertNotNull(regularJoin.joinMessage()); assertTrue(visibility.isPubliclyVisible(ordinary));
        visibility.setHidden(survivor, true);
        module.onQuit(new PlayerQuitEvent(admin, Component.text("left"), PlayerQuitEvent.QuitReason.DISCONNECTED));
        var rejoin = new PlayerJoinEvent(admin, Component.text("joined again")); module.onJoin(rejoin);
        assertTrue(visibility.isPubliclyVisible(survivor)); assertNotNull(rejoin.joinMessage());
    }
    private static void field(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static Player player(UUID id, boolean op, List<String> hidden) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p,m,a) -> switch(m.getName()) {
            case "getUniqueId" -> id; case "isOp" -> op;
            case "hidePlayer" -> { hidden.add(id + ":" + ((Player)a[1]).getUniqueId()); yield null; }
            default -> throw new UnsupportedOperationException(m.getName());
        });
    }
}
