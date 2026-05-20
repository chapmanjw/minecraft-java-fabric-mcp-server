package com.chapmanjw.minecraft.fabric.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void allowsRequestsUpToBucketCapacity() {
        RateLimiter limiter = new RateLimiter(5);
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("client-a"), "request " + i + " should be allowed");
        }
        assertFalse(limiter.tryAcquire("client-a"), "burst budget should be exhausted");
    }

    @Test
    void separateClientsHaveSeparateBuckets() {
        RateLimiter limiter = new RateLimiter(2);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        // Client b is untouched.
        assertTrue(limiter.tryAcquire("b"));
        assertTrue(limiter.tryAcquire("b"));
        assertFalse(limiter.tryAcquire("b"));
    }
}
