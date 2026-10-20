package de.illunium.custom.core;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * Volatile pending requests (invites, confirmations). An entry is valid while {@code now < created + ttl}.
 * Main thread only; nothing survives a restart.
 */
public final class ExpiringMap<K, V> {

    private record Entry<V>(V value, Instant expires) {
    }

    private final Map<K, Entry<V>> entries = new HashMap<>();
    private final InstantSource clock;
    private final Duration ttl;

    public ExpiringMap(InstantSource clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public void put(K key, V value) {
        entries.put(key, new Entry<>(value, clock.instant().plus(ttl)));
    }

    public Optional<V> get(K key) {
        purge();
        return Optional.ofNullable(entries.get(key)).map(Entry::value);
    }

    public Optional<V> remove(K key) {
        purge();
        return Optional.ofNullable(entries.remove(key)).map(Entry::value);
    }

    public void removeIf(BiPredicate<K, V> filter) {
        entries.entrySet().removeIf(e -> filter.test(e.getKey(), e.getValue().value()));
    }

    /** Snapshot of all valid entries. */
    public Map<K, V> all() {
        purge();
        Map<K, V> copy = new HashMap<>();
        entries.forEach((k, e) -> copy.put(k, e.value()));
        return copy;
    }

    private void purge() {
        Instant now = clock.instant();
        entries.values().removeIf(e -> !now.isBefore(e.expires()));
    }
}
