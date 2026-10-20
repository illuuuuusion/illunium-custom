package de.illunium.custom.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.illunium.custom.TestMainThread;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExpiringMapTest {

    @Test
    void validUntilJustBeforeTtl() {
        TestMainThread main = new TestMainThread();
        ExpiringMap<String, String> map = new ExpiringMap<>(main.clock(), Duration.ofSeconds(300));
        map.put("a", "x");

        main.advance(Duration.ofMillis(299_999));
        assertEquals(Optional.of("x"), map.get("a"));
        main.advance(Duration.ofMillis(1));
        assertTrue(map.get("a").isEmpty());
        assertTrue(map.all().isEmpty());
    }

    @Test
    void putRefreshesAndRemoveReturnsOnlyValidEntries() {
        TestMainThread main = new TestMainThread();
        ExpiringMap<String, String> map = new ExpiringMap<>(main.clock(), Duration.ofSeconds(30));
        map.put("a", "x");
        main.advance(Duration.ofSeconds(20));
        map.put("a", "y");
        main.advance(Duration.ofSeconds(20));
        assertEquals(Optional.of("y"), map.remove("a"));
        assertTrue(map.remove("a").isEmpty());
    }
}
