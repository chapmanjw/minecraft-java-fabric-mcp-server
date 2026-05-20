package com.chapmanjw.minecraft.fabric.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EventTypeTest {

    @Test
    void everyValueHasUniqueWireName() {
        for (EventType t : EventType.values()) {
            assertEquals(t, EventType.fromWireName(t.wireName()).orElseThrow());
        }
    }

    @Test
    void unknownWireNameReturnsEmpty() {
        assertTrue(EventType.fromWireName("nope.does.not.exist").isEmpty());
        assertTrue(EventType.fromWireName("").isEmpty());
    }

    @Test
    void nullWireNameReturnsEmpty() {
        assertTrue(EventType.fromWireName(null).isEmpty());
    }

    @Test
    void wireNamesAreDomainDotVerb() {
        // Spot-check the documented naming convention so a typo in any value gets
        // caught at test time.
        assertEquals("server.tick", EventType.SERVER_TICK.wireName());
        assertEquals("player.join", EventType.PLAYER_JOIN.wireName());
        assertEquals("block.break", EventType.BLOCK_BREAK.wireName());
        assertEquals("container.close", EventType.CONTAINER_CLOSE.wireName());
        for (EventType t : EventType.values()) {
            assertTrue(t.wireName().contains("."), t.wireName());
        }
    }
}
