package com.chapmanjw.minecraft.fabric.mcp.protocol;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.runtime.EventRingBuffer;

/**
 * Tracks event subscriptions for {@code events_subscribe} / {@code events_poll}.
 *
 * <p>Each subscription owns its own {@link EventRingBuffer}. When the server-side
 * event-source callbacks (registered against Fabric API at startup) fire a Minecraft
 * event, this bus routes it to every matching subscription. Subscriptions that no
 * MCP client has polled for a while still consume memory — set
 * {@code event_buffer_size} reasonably and clients should poll regularly.
 */
public final class EventBus {

    private final int defaultBufferSize;
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public EventBus(int defaultBufferSize) {
        if (defaultBufferSize < 16) {
            throw new IllegalArgumentException("defaultBufferSize must be >= 16");
        }
        this.defaultBufferSize = defaultBufferSize;
    }

    /**
     * Register a subscription. The returned ID is opaque and unique to this server
     * lifetime; clients pass it back to {@code events_poll} / {@code events_unsubscribe}.
     *
     * @param types non-empty set of event types to capture
     * @param filters optional filter map; semantics are event-type specific (e.g. a
     *     {@code player_uuid} filter on {@code player.chat})
     */
    public String subscribe(EnumSet<EventType> types, Map<String, JsonNode> filters) {
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("subscribe: types must be non-empty");
        }
        String id = UUID.randomUUID().toString();
        Subscription sub =
                new Subscription(
                        id,
                        EnumSet.copyOf(types),
                        Map.copyOf(filters == null ? Map.of() : filters),
                        new EventRingBuffer<>(defaultBufferSize));
        subscriptions.put(id, sub);
        return id;
    }

    public Optional<Subscription> get(String id) {
        return Optional.ofNullable(subscriptions.get(id));
    }

    public List<Subscription> list() {
        return new ArrayList<>(subscriptions.values());
    }

    public boolean unsubscribe(String id) {
        return subscriptions.remove(id) != null;
    }

    public int size() {
        return subscriptions.size();
    }

    /**
     * Publish an event to every matching subscription. Called from the Minecraft
     * main thread (where Fabric API events fire).
     */
    public void publish(EventEnvelope event) {
        for (Subscription s : subscriptions.values()) {
            if (s.matches(event)) {
                s.buffer.offer(event);
            }
        }
    }

    /** A registered subscription. Immutable apart from the ring buffer. */
    public static final class Subscription {
        private final String id;
        private final EnumSet<EventType> types;
        private final Map<String, JsonNode> filters;
        private final EventRingBuffer<EventEnvelope> buffer;

        Subscription(
                String id,
                EnumSet<EventType> types,
                Map<String, JsonNode> filters,
                EventRingBuffer<EventEnvelope> buffer) {
            this.id = id;
            this.types = types;
            this.filters = filters;
            this.buffer = buffer;
        }

        public String id() {
            return id;
        }

        public EnumSet<EventType> types() {
            return EnumSet.copyOf(types);
        }

        public Map<String, JsonNode> filters() {
            return filters;
        }

        public List<EventEnvelope> drain(int max) {
            return buffer.drain(max);
        }

        public long droppedCount() {
            return buffer.droppedCount();
        }

        public int bufferSize() {
            return buffer.size();
        }

        boolean matches(EventEnvelope event) {
            if (!types.contains(event.type())) {
                return false;
            }
            if (filters.isEmpty()) {
                return true;
            }
            JsonNode payload = event.payload();
            if (payload == null) {
                return false;
            }
            for (Map.Entry<String, JsonNode> filter : filters.entrySet()) {
                JsonNode actual = payload.get(filter.getKey());
                if (actual == null || !actual.equals(filter.getValue())) {
                    return false;
                }
            }
            return true;
        }
    }
}
