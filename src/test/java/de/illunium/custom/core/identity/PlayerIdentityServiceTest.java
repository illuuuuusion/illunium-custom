package de.illunium.custom.core.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.illunium.custom.storage.Database;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerIdentityServiceTest {

    private static final Logger LOGGER = Logger.getLogger("test");

    @TempDir
    Path dir;

    @Test
    void remembersAcrossRestartAndFindsNamesCaseInsensitively() throws Exception {
        UUID steve = UUID.randomUUID();
        UUID renamed = UUID.randomUUID();
        Path file = dir.resolve("data.db");

        try (Database db = Database.open(file)) {
            PlayerIdentityService identities = PlayerIdentityService.load(db, LOGGER);
            identities.remember(steve, "Steve");
            identities.remember(renamed, "Alex");
            identities.remember(renamed, "Steve_");
        }

        try (Database db = Database.open(file)) {
            PlayerIdentityService identities = PlayerIdentityService.load(db, LOGGER);
            assertEquals(Optional.of("Steve_"), identities.name(renamed));
            assertEquals(java.util.List.of(steve), identities.findByName("sTEVE"));
            assertTrue(identities.findByName("Alex").isEmpty());

            UUID other = UUID.randomUUID();
            identities.remember(other, "steve");
            assertEquals(Set.of(steve, other), Set.copyOf(identities.findByName("Steve")));
        }
    }
}
