package com.chapmanjw.minecraft.fabric.mcp.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

/**
 * Additional coverage for {@link MinecraftMainThreadExecutor} edge cases not in the
 * primary test class — constructor validation, void-return submission, default-timeout
 * variants, scheduler-throws-on-accept behavior, and the diagnostic exception types.
 */
class MinecraftMainThreadExecutorExtraTest {

    @Test
    void constructorRejectsNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new MinecraftMainThreadExecutor(0));
        assertThrows(IllegalArgumentException.class, () -> new MinecraftMainThreadExecutor(-1));
    }

    @Test
    void isAttachedReportsLifecycleState() {
        var exec = new MinecraftMainThreadExecutor(500);
        assertFalse(exec.isAttached());
        exec.attach(Runnable::run);
        assertTrue(exec.isAttached());
        exec.detach();
        assertFalse(exec.isAttached());
    }

    @Test
    void submitVoidRunsWorkAndCompletesFuture() throws Exception {
        var exec = new MinecraftMainThreadExecutor(500);
        exec.attach(Runnable::run);
        AtomicBoolean ran = new AtomicBoolean();
        CompletableFuture<Void> f = exec.submitVoid(() -> ran.set(true));
        f.get(1, TimeUnit.SECONDS);
        assertTrue(ran.get());
    }

    @Test
    void submitBlockingUsesConfiguredDefaultTimeout() {
        var exec = new MinecraftMainThreadExecutor(50);
        exec.attach(r -> {
            // Never run the work — every call times out.
        });
        // The two-arg overload exists; this verifies the default-timeout overload routes
        // through the same code path with the configured timeout.
        assertThrows(
                java.util.concurrent.TimeoutException.class,
                () -> exec.submitBlocking(() -> "x"));
    }

    @Test
    void submitWrapsSchedulerRuntimeException() {
        var exec = new MinecraftMainThreadExecutor(500);
        exec.attach(r -> {
            throw new RuntimeException("scheduler refused");
        });
        CompletableFuture<String> f = exec.submit(() -> "x");
        assertTrue(f.isCompletedExceptionally());
        ExecutionException ee = assertThrows(ExecutionException.class, f::get);
        assertNotNull(ee.getCause());
        assertTrue(ee.getCause().getMessage().contains("scheduler refused"));
    }

    @Test
    void submitRejectsNullWork() {
        var exec = new MinecraftMainThreadExecutor(500);
        exec.attach(Runnable::run);
        assertThrows(NullPointerException.class, () -> exec.submit(null));
    }

    @Test
    void submitVoidRejectsNullWork() {
        var exec = new MinecraftMainThreadExecutor(500);
        exec.attach(Runnable::run);
        assertThrows(NullPointerException.class, () -> exec.submitVoid(null));
    }

    @Test
    void attachRejectsNullScheduler() {
        var exec = new MinecraftMainThreadExecutor(500);
        assertThrows(NullPointerException.class, () -> exec.attach(null));
    }

    @Test
    void mainThreadWorkExceptionCarriesMessageAndCause() {
        Throwable cause = new IllegalStateException("inner");
        var ex = new MinecraftMainThreadExecutor.MainThreadWorkException("outer", cause);
        assertEquals("outer", ex.getMessage());
        assertEquals(cause, ex.getCause());

        var noCause = new MinecraftMainThreadExecutor.MainThreadWorkException("just-msg");
        assertEquals("just-msg", noCause.getMessage());
        assertNull(noCause.getCause());
    }

    @Test
    void mainThreadNotAvailableExceptionCarriesMessage() {
        var ex = new MinecraftMainThreadExecutor.MainThreadNotAvailableException("oops");
        assertEquals("oops", ex.getMessage());
    }

    @Test
    void submitBlockingRootCauseMessageIncludesNestedException() {
        var exec = new MinecraftMainThreadExecutor(500);
        exec.attach(Runnable::run);
        var wrapped =
                new RuntimeException("outer", new IllegalArgumentException("inner cause"));
        var ex =
                assertThrows(
                        MinecraftMainThreadExecutor.MainThreadWorkException.class,
                        () ->
                                exec.submitBlocking(
                                        () -> {
                                            throw wrapped;
                                        },
                                        500));
        assertTrue(ex.getMessage().contains("inner cause"), ex.getMessage());
    }
}
