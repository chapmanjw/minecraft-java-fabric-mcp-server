package com.chapmanjw.minecraft.fabric.mcp.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EventRingBufferTest {

    @Test
    void capacityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new EventRingBuffer<String>(0));
        assertThrows(IllegalArgumentException.class, () -> new EventRingBuffer<String>(-1));
    }

    @Test
    void offerAndDrainPreservesOrder() {
        var buf = new EventRingBuffer<Integer>(4);
        buf.offer(1);
        buf.offer(2);
        buf.offer(3);
        assertEquals(List.of(1, 2, 3), buf.drain(10));
        assertEquals(0L, buf.droppedCount());
    }

    @Test
    void dropsOldestOnOverflow() {
        var buf = new EventRingBuffer<Integer>(3);
        for (int i = 1; i <= 6; i++) {
            buf.offer(i);
        }
        // The latest 3 win; the first 3 are dropped.
        assertEquals(List.of(4, 5, 6), buf.drain(10));
        assertEquals(3L, buf.droppedCount());
    }

    @Test
    void drainRespectsMaxArgument() {
        var buf = new EventRingBuffer<Integer>(5);
        for (int i = 1; i <= 5; i++) {
            buf.offer(i);
        }
        assertEquals(List.of(1, 2), buf.drain(2));
        assertEquals(List.of(3, 4, 5), buf.drain(10));
    }

    @Test
    void clearWipesBufferButKeepsDropCounter() {
        var buf = new EventRingBuffer<Integer>(2);
        buf.offer(1);
        buf.offer(2);
        buf.offer(3); // drops 1
        assertEquals(1L, buf.droppedCount());
        buf.clear();
        assertTrue(buf.drain(10).isEmpty());
        assertEquals(1L, buf.droppedCount());
    }
}
