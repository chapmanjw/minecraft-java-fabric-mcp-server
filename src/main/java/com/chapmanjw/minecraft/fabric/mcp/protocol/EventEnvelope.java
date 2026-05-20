package com.chapmanjw.minecraft.fabric.mcp.protocol;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Envelope for an event published into the {@link EventBus}.
 *
 * <p>The payload is a JsonNode so each event type can describe whatever state is
 * relevant — coordinates, entity UUIDs, etc. — without coupling EventBus to specific
 * event shapes.
 */
public record EventEnvelope(EventType type, Instant timestamp, JsonNode payload) {

    public static EventEnvelope now(EventType type, JsonNode payload) {
        return new EventEnvelope(type, Instant.now(), payload);
    }
}
