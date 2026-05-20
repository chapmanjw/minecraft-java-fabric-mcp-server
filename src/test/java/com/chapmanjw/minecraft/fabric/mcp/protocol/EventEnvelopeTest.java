package com.chapmanjw.minecraft.fabric.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void nowSetsTypeAndPayloadAndTimestamp() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("player_uuid", "00000000-0000-0000-0000-000000000001");
        Instant before = Instant.now();
        EventEnvelope env = EventEnvelope.now(EventType.PLAYER_JOIN, payload);
        Instant after = Instant.now();
        assertEquals(EventType.PLAYER_JOIN, env.type());
        assertSame(payload, env.payload());
        assertNotNull(env.timestamp());
        assertTrue(
                !env.timestamp().isBefore(before) && !env.timestamp().isAfter(after),
                "timestamp must be within [before, after]");
    }

    @Test
    void recordConstructorAccessors() {
        JsonNode p = JsonNodeFactory.instance.objectNode().put("k", "v");
        Instant ts = Instant.parse("2026-01-01T00:00:00Z");
        EventEnvelope env = new EventEnvelope(EventType.BLOCK_PLACE, ts, p);
        assertEquals(EventType.BLOCK_PLACE, env.type());
        assertEquals(ts, env.timestamp());
        assertEquals("v", env.payload().path("k").asText());
    }
}
