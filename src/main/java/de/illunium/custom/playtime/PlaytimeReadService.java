package de.illunium.custom.playtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Read on the main thread: captures live sessions before the queued bulk database read. */
public interface PlaytimeReadService {
    boolean available();
    CompletableFuture<Map<UUID, Long>> snapshot();
}
