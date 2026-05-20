package com.chapmanjw.minecraft.fabric.mcp.runtime;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Marshals work from arbitrary threads onto Minecraft's server main thread.
 *
 * <p>Minecraft runs all world logic on a single thread. Touching a {@code ServerLevel},
 * an {@code Entity}, or any world state from any other thread is undefined behavior.
 * Every MCP tool handler must therefore route its work through this executor before
 * reading or writing world state.
 *
 * <p>The class is wired to {@code MinecraftServer::execute} at server-starting time via
 * {@link #attach(Consumer)}. Until then, work submitted to this executor is buffered
 * locally and run on the first tick after attachment. Once the server stops, the
 * scheduler is detached and further submissions complete exceptionally — that's a
 * graceful failure mode for HTTP requests that race the stop.
 *
 * <p>Threading: thread-safe. The same instance is reused across the server lifetime;
 * lifecycle hooks (server-starting / server-stopping) call {@link #attach(Consumer)}
 * and {@link #detach()} to track the underlying scheduler.
 */
public final class MinecraftMainThreadExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft_fabric_mcp/runtime");

    /**
     * Holder for the active scheduler. {@code null} until {@link #attach(Consumer)} is
     * called by the lifecycle hook. Replaced atomically so submit-after-detach can see
     * the change without locks.
     */
    private final AtomicReference<Consumer<Runnable>> scheduler = new AtomicReference<>();

    /**
     * Default per-call timeout for {@link #submitBlocking(Supplier, long)} when the
     * caller does not supply one. Read at startup from the config.
     */
    private final long defaultTimeoutMs;

    public MinecraftMainThreadExecutor(long defaultTimeoutMs) {
        if (defaultTimeoutMs <= 0) {
            throw new IllegalArgumentException("defaultTimeoutMs must be > 0");
        }
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    // --- lifecycle -----------------------------------------------------------

    /**
     * Attach the underlying scheduler. Pass {@code minecraftServer::execute} in
     * production; tests pass a direct-run consumer.
     */
    public void attach(Consumer<Runnable> mainThreadScheduler) {
        Objects.requireNonNull(mainThreadScheduler, "mainThreadScheduler");
        scheduler.set(mainThreadScheduler);
    }

    /** Detach the scheduler. Subsequent {@link #submit} calls fail fast. */
    public void detach() {
        scheduler.set(null);
    }

    /** True once a scheduler is attached. */
    public boolean isAttached() {
        return scheduler.get() != null;
    }

    // --- submit --------------------------------------------------------------

    /**
     * Schedule {@code work} to run on the main thread. The returned future completes
     * with the supplier's result, or completes exceptionally if {@code work} throws
     * or the scheduler is detached before the work runs.
     */
    public <T> CompletableFuture<T> submit(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        CompletableFuture<T> future = new CompletableFuture<>();
        Consumer<Runnable> current = scheduler.get();
        if (current == null) {
            future.completeExceptionally(
                    new MainThreadNotAvailableException(
                            "MCP main-thread executor is not attached — the Minecraft server is not running"));
            return future;
        }
        try {
            current.accept(
                    () -> {
                        try {
                            future.complete(work.get());
                        } catch (Throwable t) {
                            future.completeExceptionally(t);
                        }
                    });
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /** Convenience overload for work that returns no value. */
    public CompletableFuture<Void> submitVoid(Runnable work) {
        Objects.requireNonNull(work, "work");
        return submit(
                () -> {
                    work.run();
                    return null;
                });
    }

    /**
     * Schedule {@code work}, wait up to {@code timeoutMs} milliseconds for it to
     * complete, and return its result. The calling thread is blocked.
     *
     * <p>This is the canonical entry point for tool handlers: they parse on the HTTP
     * thread, call this once for the world-touching portion, and serialize the result
     * back to JSON on the HTTP thread.
     *
     * @throws TimeoutException if {@code work} does not complete within {@code timeoutMs}
     * @throws MainThreadWorkException if {@code work} threw an exception or the
     *     executor was detached
     */
    public <T> T submitBlocking(Supplier<T> work, long timeoutMs)
            throws TimeoutException, MainThreadWorkException {
        CompletableFuture<T> future = submit(work);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MainThreadWorkException("Interrupted while waiting for main-thread work", e);
        } catch (ExecutionException e) {
            throw new MainThreadWorkException(
                    "Main-thread work failed: " + rootCauseMessage(e), e.getCause());
        }
    }

    /** {@link #submitBlocking(Supplier, long)} with the configured default timeout. */
    public <T> T submitBlocking(Supplier<T> work) throws TimeoutException, MainThreadWorkException {
        return submitBlocking(work, defaultTimeoutMs);
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t.getCause() == null ? t : t.getCause();
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    /** Sentinel exception thrown when the executor is not attached at submit time. */
    public static final class MainThreadNotAvailableException extends RuntimeException {
        public MainThreadNotAvailableException(String message) {
            super(message);
        }
    }

    /** Exception wrapping any failure inside the main-thread work supplier. */
    public static final class MainThreadWorkException extends Exception {
        public MainThreadWorkException(String message, Throwable cause) {
            super(message, cause);
        }

        public MainThreadWorkException(String message) {
            super(message);
        }
    }
}
