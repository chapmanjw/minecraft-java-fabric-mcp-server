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

    /**
     * Live subscription count per event type, so a high-frequency source (fluid flow fires on
     * every fluid tick) can skip all work with one read when nobody is listening.
     */
    private final java.util.concurrent.atomic.AtomicIntegerArray subscribersByType =
            new java.util.concurrent.atomic.AtomicIntegerArray(EventType.values().length);

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
        return subscribe(types, filters, null);
    }

    /**
     * Register a subscription with a region gate: events of the gate's type are delivered only
     * when they fall inside its box, and at most at its rate. Other types in the same
     * subscription are unaffected.
     */
    public String subscribe(EnumSet<EventType> types, Map<String, JsonNode> filters, RegionGate region) {
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("subscribe: types must be non-empty");
        }
        String id = UUID.randomUUID().toString();
        Subscription sub =
                new Subscription(
                        id,
                        EnumSet.copyOf(types),
                        Map.copyOf(filters == null ? Map.of() : filters),
                        new EventRingBuffer<>(defaultBufferSize),
                        region);
        subscriptions.put(id, sub);
        for (EventType t : sub.types) {
            subscribersByType.incrementAndGet(t.ordinal());
        }
        return id;
    }

    public Optional<Subscription> get(String id) {
        return Optional.ofNullable(subscriptions.get(id));
    }

    public List<Subscription> list() {
        return new ArrayList<>(subscriptions.values());
    }

    public boolean unsubscribe(String id) {
        Subscription removed = subscriptions.remove(id);
        if (removed == null) {
            return false;
        }
        for (EventType t : removed.types) {
            subscribersByType.decrementAndGet(t.ordinal());
        }
        return true;
    }

    /** True when at least one subscription asks for {@code type}. One array read. */
    public boolean hasSubscribers(EventType type) {
        return subscribersByType.get(type.ordinal()) > 0;
    }

    /**
     * True when some subscription for {@code type} has a region gate covering the position.
     * Lets a high-frequency source skip building a payload that no one would receive.
     */
    public boolean anyRegionContains(EventType type, String dimension, int x, int y, int z) {
        for (Subscription s : subscriptions.values()) {
            if (s.types.contains(type) && s.region != null && s.region.contains(dimension, x, y, z)) {
                return true;
            }
        }
        return false;
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
        private final RegionGate region;

        Subscription(
                String id,
                EnumSet<EventType> types,
                Map<String, JsonNode> filters,
                EventRingBuffer<EventEnvelope> buffer,
                RegionGate region) {
            this.id = id;
            this.types = types;
            this.filters = filters;
            this.buffer = buffer;
            this.region = region;
        }

        /** The region gate, or null when the subscription has none. */
        public RegionGate region() {
            return region;
        }

        /** Events the region gate's rate limit discarded. */
        public long rateLimitedCount() {
            return region == null ? 0L : region.rateLimitedCount();
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
            if (region != null && region.type() == event.type()) {
                JsonNode p = event.payload();
                if (p == null
                        || !region.contains(
                                p.path("dimension").asText(),
                                p.path("x").asInt(),
                                p.path("y").asInt(),
                                p.path("z").asInt())) {
                    return false;
                }
                // Equality filters are checked first so the rate budget is spent only on events
                // this subscription would actually deliver.
                if (!filtersMatch(event)) {
                    return false;
                }
                return region.tryAcquire(System.nanoTime());
            }
            return filtersMatch(event);
        }

        private boolean filtersMatch(EventEnvelope event) {
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

    /**
     * Restricts one event type of a subscription to a dimension and inclusive box, at a bounded
     * rate (token bucket refilled at {@code maxPerSecond}, burst of the same size).
     */
    public static final class RegionGate {
        private final EventType type;
        private final String dimension;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private static final double NANOS_PER_SECOND = 1_000_000_000.0;
        private final int maxPerSecond;
        private double tokens;
        private long lastNanos;
        private final java.util.concurrent.atomic.AtomicLong rateLimited =
                new java.util.concurrent.atomic.AtomicLong();

        public RegionGate(
                EventType type,
                String dimension,
                int x1,
                int y1,
                int z1,
                int x2,
                int y2,
                int z2,
                int maxPerSecond) {
            if (maxPerSecond < 1) {
                throw new IllegalArgumentException("maxPerSecond must be >= 1");
            }
            this.type = type;
            this.dimension = dimension;
            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.maxZ = Math.max(z1, z2);
            this.maxPerSecond = maxPerSecond;
            this.tokens = maxPerSecond;
            this.lastNanos = System.nanoTime();
        }

        public EventType type() {
            return type;
        }

        public boolean contains(String dim, int x, int y, int z) {
            return dimension.equals(dim)
                    && x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        /** Spend one token if available; otherwise count the event as rate-limited. */
        synchronized boolean tryAcquire(long nowNanos) {
            double elapsed = Math.max(0L, nowNanos - lastNanos) / NANOS_PER_SECOND;
            lastNanos = nowNanos;
            tokens = Math.min(maxPerSecond, tokens + elapsed * maxPerSecond);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            rateLimited.incrementAndGet();
            return false;
        }

        public long rateLimitedCount() {
            return rateLimited.get();
        }

        /** JSON-ready description for subscription listings. */
        public java.util.Map<String, Object> describe() {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("event_type", type.wireName());
            m.put("dimension", dimension);
            m.put("from", new int[] {minX, minY, minZ});
            m.put("to", new int[] {maxX, maxY, maxZ});
            m.put("max_per_second", maxPerSecond);
            return m;
        }
    }
}
