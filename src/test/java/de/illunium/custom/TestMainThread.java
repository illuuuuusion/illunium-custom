package de.illunium.custom;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.illunium.custom.core.command.UserException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Stands in for the server thread: queued tasks run only on the test thread, inside {@link #await}. */
public final class TestMainThread implements Executor {

    private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private Instant now = Instant.parse("2026-10-20T12:00:00Z");

    @Override
    public void execute(Runnable task) {
        tasks.add(task);
    }

    public <T> T await(CompletableFuture<T> future) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!future.isDone() || !tasks.isEmpty()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out");
            }
            try {
                Runnable task = tasks.poll(5, TimeUnit.MILLISECONDS);
                if (task != null) {
                    task.run();
                }
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        return future.join();
    }

    /** Awaits a future that must fail with a {@link UserException}; returns its message. */
    public String fails(CompletableFuture<?> future) {
        CompletionException error = assertThrows(CompletionException.class, () -> await(future));
        return assertInstanceOf(UserException.class, error.getCause()).getMessage();
    }

    public InstantSource clock() {
        return () -> now;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }
}
