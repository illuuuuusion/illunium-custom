package de.illunium.custom.whiteliststaging;

import com.destroystokyo.paper.profile.PlayerProfile;
import de.illunium.custom.core.command.UserException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.bukkit.Server;

/** Only the documented blocking Paper profile API runs here; at most two network requests and 32 queued. */
final class ProfileResolver implements AutoCloseable {
    private final Server server;
    private final java.util.concurrent.Executor mainThread;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), task -> { Thread t = new Thread(task, "IlluniumCustom-Profiles"); t.setDaemon(true); return t; });
    ProfileResolver(Server server, java.util.concurrent.Executor mainThread) { this.server = server; this.mainThread = mainThread; }

    CompletableFuture<VerifiedProfile> name(String name) {
        VerifiedProfile.validateName(name);
        return resolve(server.createProfile(name), p -> {
            if (!p.name().equalsIgnoreCase(name)) throw new UserException("Veralteter Name; bitte aktuellen Minecraft-Namen verwenden.");
            return p;
        });
    }
    CompletableFuture<VerifiedProfile> refresh(VerifiedProfile expected) {
        // Resolve by UUID first, then verify the current name still maps to that same account and warm the native name cache.
        return resolve(server.createProfile(expected.uuid()), p -> p).thenComposeAsync(actual ->
                name(actual.name()).thenApply(confirmed -> {
                    if (!confirmed.uuid().equals(expected.uuid())) throw new UserException("Name gehört inzwischen zu einem anderen Account.");
                    return confirmed;
                }), mainThread);
    }

    private CompletableFuture<VerifiedProfile> resolve(PlayerProfile profile, Function<VerifiedProfile, VerifiedProfile> verify) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                if (!profile.complete(true, true)) throw new UserException("Mojang-Profil nicht vollständig.");
                return verify.apply(VerifiedProfile.from(profile));
            }, executor).orTimeout(15, TimeUnit.SECONDS).exceptionally(error -> {
                Throwable cause = error;
                while (cause.getCause() != null) cause = cause.getCause();
                if (cause instanceof UserException user) throw user;
                throw new UserException("Profilabfrage fehlgeschlagen oder nach 15 s abgelaufen; bitte später wiederholen.");
            });
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(new UserException("Profilabfragen ausgelastet; bitte später wiederholen."));
        }
    }
    @Override public void close() { executor.shutdownNow(); }
}
