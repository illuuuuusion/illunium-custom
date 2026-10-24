package de.illunium.custom.whiteliststaging;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.JsonParser;
import de.illunium.custom.core.command.UserException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

record VerifiedProfile(UUID uuid, String name) {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    VerifiedProfile {
        validateName(name);
        if (uuid == null || uuid.version() != 4) throw new UserException("Kein verifiziertes Minecraft-Java-Profil.");
    }
    static void validateName(String name) {
        if (name == null || !NAME.matcher(name).matches()) throw new UserException("Ungültiger Minecraft-Name (3–16 Zeichen: A–Z, 0–9, _).");
    }
    static VerifiedProfile from(PlayerProfile profile) {
        for (var property : profile.getProperties()) {
            if (!property.getName().equals("textures")) continue;
            var json = JsonParser.parseString(new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8)).getAsJsonObject();
            UUID id = UUID.fromString(json.get("profileId").getAsString().replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            if (!id.equals(profile.getId())) throw new UserException("Profil-UUID stimmt nicht mit Mojang überein.");
            return new VerifiedProfile(id, json.get("profileName").getAsString());
        }
        throw new UserException("Account unbekannt oder Mojang nicht erreichbar; nicht vorgemerkt.");
    }
}
