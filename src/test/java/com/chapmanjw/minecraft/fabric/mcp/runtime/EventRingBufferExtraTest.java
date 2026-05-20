package com.chapmanjw.minecraft.fabric.mcp.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Additional edge cases for {@link EventRingBuffer}. */
class EventRingBufferExtraTest {

    @Test
    void drainWithMaxZeroReturnsEmpty() {
        var buf = new EventRingBuffer<Integer>(4);
        buf.offer(1);
        buf.offer(2);
        List<Integer> drained = buf.drain(0);
        assertTrue(drained.isEmpty());
        // Buffer is unchanged.
        assertEquals(2, buf.size());
    }

    @Test
    void drainRejectsNegativeMax() {
        var buf = new EventRingBuffer<Integer>(4);
        assertThrows(IllegalArgumentException.class, () -> buf.drain(-1));
    }

    @Test
    void sizeAndCapacityReflectState() {
        var buf = new EventRingBuffer<Integer>(3);
        assertEquals(3, buf.capacity());
        assertEquals(0, buf.size());
        buf.offer(1);
        buf.offer(2);
        assertEquals(2, buf.size());
        buf.drain(1);
        assertEquals(1, buf.size());
    }

    @Test
    void drainOnEmptyBufferReturnsEmptyList() {
        var buf = new EventRingBuffer<String>(2);
        assertTrue(buf.drain(10).isEmpty());
    }
}
