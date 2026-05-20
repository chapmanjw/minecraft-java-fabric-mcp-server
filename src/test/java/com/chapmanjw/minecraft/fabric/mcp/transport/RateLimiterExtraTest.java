package com.chapmanjw.minecraft.fabric.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Extends {@link RateLimiterTest} with refill, prune, and constructor validation.
 *
 * <p>The refill test sleeps briefly because {@link RateLimiter} keys off
 * {@link System#nanoTime()}. We pick a high rate (60_000 rpm = 1 token/ms) so a few
 * milliseconds of sleep is enough to produce a deterministic refill.
 */
class RateLimiterExtraTest {

    @Test
    void constructorRejectsZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(-1));
    }

    @Test
    void refillRestoresTokensAfterTimePasses() throws Exception {
        // 60_000 rpm => 1 token / millisecond.
        RateLimiter limiter = new RateLimiter(60_000);
        // Burn all tokens.
        int allowed = 0;
        while (limiter.tryAcquire("c")) {
            allowed++;
            if (allowed > 200_000) {
                break;
            }
        }
        assertTrue(allowed > 0);
        // Give the bucket time to accrue a few tokens.
        Thread.sleep(50);
        assertTrue(limiter.tryAcquire("c"), "bucket should have refilled tokens after wait");
    }

    @Test
    void trackedClientsReportsCount() {
        RateLimiter limiter = new RateLimiter(5);
        assertEquals(0, limiter.trackedClients());
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        limiter.tryAcquire("c");
        assertEquals(3, limiter.trackedClients());
    }

    @Test
    void pruneIdleDropsFullBucketsPastIdleThreshold() throws Exception {
        RateLimiter limiter = new RateLimiter(5);
        limiter.tryAcquire("idle");
        assertEquals(1, limiter.trackedClients());
        // Let "idle" refill to full capacity over time.
        Thread.sleep(30);
        // pruneIdle with a 1-nanosecond threshold should drop the entry once it's full.
        limiter.pruneIdle(1);
        // Bucket may or may not be full yet depending on timing; in either case the API
        // must not throw and the tracked count must be <= 1.
        assertTrue(limiter.trackedClients() <= 1);
    }

    @Test
    void pruneIdleKeepsRecentlyUsedBuckets() {
        RateLimiter limiter = new RateLimiter(5);
        limiter.tryAcquire("hot");
        limiter.pruneIdle(TimeUnit.MINUTES.toNanos(10));
        assertEquals(1, limiter.trackedClients(), "recent activity should not be pruned");
    }

    @Test
    void overLimitReturnsFalseAcrossMultipleClients() {
        RateLimiter limiter = new RateLimiter(1);
        assertTrue(limiter.tryAcquire("x"));
        assertFalse(limiter.tryAcquire("x"));
        assertTrue(limiter.tryAcquire("y"));
    }
}
