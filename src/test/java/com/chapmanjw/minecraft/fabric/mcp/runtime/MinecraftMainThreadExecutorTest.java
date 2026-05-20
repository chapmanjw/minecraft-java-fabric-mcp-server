package com.chapmanjw.minecraft.fabric.mcp.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

class MinecraftMainThreadExecutorTest {

    @Test
    void submitFailsWhenNotAttached() {
        var exec = new MinecraftMainThreadExecutor(1000);
        CompletableFuture<String> f = exec.submit(() -> "x");
        assertTrue(f.isCompletedExceptionally());
    }

    @Test
    void submitDeliversValueViaScheduler() throws Exception {
        var exec = new MinecraftMainThreadExecutor(1000);
        // Direct-execute scheduler — runs on the calling thread immediately.
        exec.attach(Runnable::run);
        CompletableFuture<String> f = exec.submit(() -> "hello");
        assertEquals("hello", f.get());
    }

    @Test
    void submitBlockingTimesOutWhenSchedulerStalls() {
        var queue = new ArrayBlockingQueue<Runnable>(8);
        var exec = new MinecraftMainThreadExecutor(50);
        exec.attach(queue::offer); // enqueues but never drains.
        assertThrows(TimeoutException.class, () -> exec.submitBlocking(() -> "x", 50));
    }

    @Test
    void submitBlockingWrapsExceptionsFromWork() {
        var exec = new MinecraftMainThreadExecutor(500);
        exec.attach(Runnable::run);
        MinecraftMainThreadExecutor.MainThreadWorkException ex =
                assertThrows(
                        MinecraftMainThreadExecutor.MainThreadWorkException.class,
                        () ->
                                exec.submitBlocking(
                                        () -> {
                                            throw new RuntimeException("kaboom");
                                        },
                                        500));
        assertNotNull(ex.getCause());
        assertTrue(ex.getMessage().contains("kaboom"));
    }

    @Test
    void detachStopsAcceptingNewWork() {
        var exec = new MinecraftMainThreadExecutor(500);
        exec.attach(Runnable::run);
        exec.detach();
        CompletableFuture<String> f = exec.submit(() -> "x");
        assertTrue(f.isCompletedExceptionally());
        ExecutionException ee = assertThrows(ExecutionException.class, f::get);
        assertTrue(ee.getCause() instanceof MinecraftMainThreadExecutor.MainThreadNotAvailableException);
    }
}
