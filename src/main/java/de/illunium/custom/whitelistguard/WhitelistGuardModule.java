package de.illunium.custom.whitelistguard;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitTask;

/**
 * Native whitelist guard (MVP §6): whitelist and enforcement stay on, configured staff stay whitelisted. Vanilla
 * {@code /whitelist} is untouched. Staff profiles are looked up once per start; the periodic check never hits the network.
 */
public final class WhitelistGuardModule implements FeatureModule {

    private BukkitTask task;

    @Override
    public String id() {
        return "whitelistguard";
    }

    @Override
    public void enable(ModuleContext context) {
        Server server = context.plugin().getServer();
        int interval = context.settings().getInt("check-interval-seconds", 30);
        if (interval < 5 || interval > 3600) {
            throw new IllegalArgumentException("whitelistguard.check-interval-seconds must be 5..3600, got " + interval);
        }
        List<?> raw = context.settings().getList("staff", List.of());
        if (raw == null) {
            throw new IllegalArgumentException("whitelistguard.staff must be a list of {uuid, name}");
        }
        List<StaffEntry> staff = StaffEntry.parse(raw);

        if (!server.getOnlineMode()) {
            context.plugin().getLogger().severe("online-mode=false: UUIDs are not verified by Mojang, so the whitelist and"
                    + " staff protection can be bypassed. Set online-mode=true.");
        }
        WhitelistGuard guard = new WhitelistGuard(server, context.plugin().getLogger(), staff);
        guard.check();
        for (StaffEntry entry : staff) {
            // With UUID and name set, update() fetches the session profile by UUID and only fills in its properties.
            server.createProfile(entry.uuid(), entry.name()).update()
                    .thenAcceptAsync(profile -> verify(guard, entry, profile), context.mainThread())
                    .exceptionally(error -> {
                        context.plugin().getLogger().log(Level.SEVERE, "Profile lookup for staff " + entry.name() + " failed", error);
                        return null;
                    });
        }
        long ticks = interval * 20L;
        task = server.getScheduler().runTaskTimer(context.plugin(), guard::check, ticks, ticks);
        context.plugin().getLogger().info("Whitelist guard active: " + staff.size() + " staff entries, check every " + interval + " s");
    }

    /** Mojang's textures property carries the account's current id and name; it is missing if the UUID is unknown. */
    private static void verify(WhitelistGuard guard, StaffEntry entry, PlayerProfile profile) {
        for (ProfileProperty property : profile.getProperties()) {
            if (property.getName().equals("textures")) {
                JsonObject textures = JsonParser.parseString(
                        new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8)).getAsJsonObject();
                UUID id = UUID.fromString(textures.get("profileId").getAsString()
                        .replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                guard.verified(entry, id, textures.get("profileName").getAsString());
                return;
            }
        }
        guard.verified(entry, entry.uuid(), null);
    }

    @Override
    public void disable() {
        if (task != null) {
            task.cancel();
        }
    }
}
