package de.illunium.custom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PluginMetadataTest {

    @Test
    void declaresMainClassApiVersionAndPermissions() throws IOException {
        try (InputStream resource = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(resource, "plugin.yml must be packaged");

            String metadata = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(metadata.contains("main: de.illunium.custom.IlluniumCustomPlugin"));
            assertTrue(metadata.contains("api-version: '26.3'"));
            assertTrue(metadata.contains("illuniumcustom.use:"));
            assertTrue(metadata.contains("illuniumcustom.admin:"));
            assertTrue(metadata.contains("version: '0.1.0-rc.1'"));
            assertFalse(metadata.contains("${version}"));
            assertFalse(metadata.contains("depend:"), "must not depend on other plugins");
        }
    }
}
