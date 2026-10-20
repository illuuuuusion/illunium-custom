package de.illunium.custom.whitelistguard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** A protected staff account from {@code whitelistguard.staff}: the Mojang UUID and the profile name it must resolve to. */
record StaffEntry(UUID uuid, String name) {

    private static final Pattern UUID_TEXT = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    /** Parses {@code [{uuid, name}, ...]}; any invalid or duplicate entry fails the whole list. */
    static List<StaffEntry> parse(List<?> raw) {
        List<String> errors = new ArrayList<>();
        List<StaffEntry> entries = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            String at = "whitelistguard.staff[" + i + "]";
            if (!(raw.get(i) instanceof Map<?, ?> map)) {
                errors.add(at + ": must be {uuid: ..., name: ...}");
                continue;
            }
            Object uuidValue = map.get("uuid");
            Object nameValue = map.get("name");
            String uuidText = uuidValue == null ? "" : uuidValue.toString();
            String name = nameValue == null ? "" : nameValue.toString();
            if (!UUID_TEXT.matcher(uuidText).matches()) {
                errors.add(at + ".uuid: not a UUID with dashes: '" + uuidText + "'");
                continue;
            }
            UUID uuid = UUID.fromString(uuidText);
            // Offline-mode UUIDs are version 3 and derived from the name alone; accepting them would let anyone in.
            if (uuid.version() != 4) {
                errors.add(at + ".uuid: " + uuid + " is not a Mojang (online-mode) account UUID");
            } else if (!NAME.matcher(name).matches()) {
                errors.add(at + ".name: not a Minecraft profile name: '" + name + "'");
            } else if (!seen.add(uuid)) {
                errors.add(at + ".uuid: " + uuid + " is listed twice");
            } else {
                entries.add(new StaffEntry(uuid, name));
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid whitelistguard.staff: " + String.join("; ", errors));
        }
        return List.copyOf(entries);
    }
}
