package com.chapmanjw.minecraft.fabric.mcp.transport;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-client token-bucket rate limiter.
 *
 * <p>Each remote address (or, when authentication is enabled, each bearer token hash)
 * gets a bucket sized to {@code rateLimitRpm} tokens. The bucket refills at one token
 * per {@code 60000 / rateLimitRpm} milliseconds, capped at the bucket size — so a
 * client at rest accumulates room to burst.
 *
 * <p>Implementation: lock-free via {@link AtomicReference} CAS on a small immutable
 * state record. Each bucket entry is ~80 bytes; the map of buckets is unbounded but
 * idle entries can be evicted by {@link #pruneIdle(long)} from a periodic task.
 */
public final class RateLimiter {

    private final int capacity;
    private final long refillIntervalNanos;
    private final ConcurrentHashMap<String, AtomicReference<BucketState>> buckets =
            new ConcurrentHashMap<>();

    public RateLimiter(int rateLimitRpm) {
        if (rateLimitRpm < 1) {
            throw new IllegalArgumentException("rateLimitRpm must be >= 1");
        }
        this.capacity = rateLimitRpm;
        this.refillIntervalNanos = 60_000_000_000L / rateLimitRpm;
    }

    /**
     * Attempt to consume one token for {@code clientKey}. Returns true if a token was
     * available and consumed; false if the client is over its limit.
     */
    public boolean tryAcquire(String clientKey) {
        long now = System.nanoTime();
        AtomicReference<BucketState> ref =
                buckets.computeIfAbsent(clientKey, k -> new AtomicReference<>(new BucketState(capacity, now)));
        while (true) {
            BucketState prev = ref.get();
            long elapsed = Math.max(0L, now - prev.lastRefillNanos);
            long refillTokens = elapsed / refillIntervalNanos;
            long newTokens = Math.min(capacity, prev.tokens + refillTokens);
            long newLastRefill =
                    refillTokens > 0
                            ? prev.lastRefillNanos + refillTokens * refillIntervalNanos
                            : prev.lastRefillNanos;
            if (newTokens <= 0) {
                // No tokens — try to update the lastRefill anyway so future calls see the right base.
                // But if no refill happened, just bail.
                if (refillTokens > 0) {
                    ref.compareAndSet(prev, new BucketState(0, newLastRefill));
                }
                return false;
            }
            BucketState next = new BucketState(newTokens - 1, newLastRefill);
            if (ref.compareAndSet(prev, next)) {
                return true;
            }
            // CAS lost — loop and recompute.
        }
    }

    /** Drop buckets that have been at full capacity for {@code maxIdleNanos} or longer. */
    public void pruneIdle(long maxIdleNanos) {
        long now = System.nanoTime();
        buckets.entrySet()
                .removeIf(
                        e -> {
                            BucketState s = e.getValue().get();
                            return s.tokens >= capacity && (now - s.lastRefillNanos) > maxIdleNanos;
                        });
    }

    /** Currently-tracked client count; observability hook only. */
    public int trackedClients() {
        return buckets.size();
    }

    private record BucketState(long tokens, long lastRefillNanos) {}
}
