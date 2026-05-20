package com.chapmanjw.minecraft.fabric.mcp.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded, drop-oldest ring buffer used by event subscriptions.
 *
 * <p>Producers (Fabric API event callbacks) push events on the Minecraft server main
 * thread; consumers (HTTP threads handling {@code events_poll}) drain. When the buffer
 * is full, the oldest entry is dropped to make room for the new one and the drop
 * counter is bumped.
 *
 * <p>The buffer holds plain {@link Object} payloads so it doesn't tie this layer to
 * MCP-specific types; the protocol layer is responsible for shape and serialization.
 *
 * <p>Threading: thread-safe via a single {@link ReentrantLock}. Lock contention is
 * negligible — event publish rate per subscription is in the low thousands per second
 * at worst, and {@code events_poll} drains atomically.
 */
public final class EventRingBuffer<E> {

    private final ReentrantLock lock = new ReentrantLock();
    private final Deque<E> buffer;
    private final int capacity;
    private final AtomicLong droppedCount = new AtomicLong();

    public EventRingBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.capacity = capacity;
        this.buffer = new ArrayDeque<>(capacity);
    }

    /**
     * Add an event. If the buffer is full, the oldest element is discarded and
     * the drop counter is incremented; the new event always lands.
     */
    public void offer(E event) {
        lock.lock();
        try {
            if (buffer.size() >= capacity) {
                buffer.pollFirst();
                droppedCount.incrementAndGet();
            }
            buffer.addLast(event);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically drain at most {@code max} events from the head of the buffer.
     * Pass {@link Integer#MAX_VALUE} to drain everything.
     */
    public List<E> drain(int max) {
        if (max < 0) {
            throw new IllegalArgumentException("max must be >= 0");
        }
        if (max == 0) {
            return List.of();
        }
        lock.lock();
        try {
            int n = Math.min(max, buffer.size());
            if (n == 0) {
                return List.of();
            }
            List<E> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(buffer.pollFirst());
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    /** Total events dropped due to overflow since this buffer was constructed. */
    public long droppedCount() {
        return droppedCount.get();
    }

    /** Approximate current size; reads are not synchronized. */
    public int size() {
        lock.lock();
        try {
            return buffer.size();
        } finally {
            lock.unlock();
        }
    }

    /** Maximum capacity of this buffer. */
    public int capacity() {
        return capacity;
    }

    /** Discard all queued events. The drop counter is not reset. */
    public void clear() {
        lock.lock();
        try {
            buffer.clear();
        } finally {
            lock.unlock();
        }
    }
}
