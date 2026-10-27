package com.chapmanjw.minecraft.fabric.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

class EventBusTest {

    @Test
    void subscribeReturnsOpaqueIdAndStoresSubscription() {
        EventBus bus = new EventBus(16);
        String id = bus.subscribe(EnumSet.of(EventType.SERVER_TICK), Map.of());
        assertNotNull(id);
        assertFalse(id.isEmpty());
        assertEquals(1, bus.size());
        EventBus.Subscription s = bus.get(id).orElseThrow();
        assertEquals(id, s.id());
        assertTrue(s.types().contains(EventType.SERVER_TICK));
        assertTrue(s.filters().isEmpty());
        assertEquals(0, s.bufferSize());
        assertEquals(0L, s.droppedCount());
    }

    @Test
    void subscribeRejectsEmptyOrNullTypes() {
        EventBus bus = new EventBus(16);
        assertThrows(
                IllegalArgumentException.class,
                () -> bus.subscribe(EnumSet.noneOf(EventType.class), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> bus.subscribe(null, Map.of()));
    }

    @Test
    void constructorRejectsTinyBufferSize() {
        assertThrows(IllegalArgumentException.class, () -> new EventBus(0));
        assertThrows(IllegalArgumentException.class, () -> new EventBus(15));
    }

    @Test
    void unsubscribeRemovesEntry() {
        EventBus bus = new EventBus(16);
        String id = bus.subscribe(EnumSet.of(EventType.PLAYER_JOIN), null);
        assertTrue(bus.unsubscribe(id));
        assertFalse(bus.unsubscribe(id));
        assertEquals(0, bus.size());
        assertTrue(bus.get(id).isEmpty());
    }

    @Test
    void listReflectsRegisteredSubs() {
        EventBus bus = new EventBus(16);
        bus.subscribe(EnumSet.of(EventType.PLAYER_JOIN), null);
        bus.subscribe(EnumSet.of(EventType.PLAYER_LEAVE), null);
        assertEquals(2, bus.list().size());
    }

    @Test
    void publishDeliversOnlyToMatchingSubs() {
        EventBus bus = new EventBus(16);
        String joinSub = bus.subscribe(EnumSet.of(EventType.PLAYER_JOIN), null);
        String leaveSub = bus.subscribe(EnumSet.of(EventType.PLAYER_LEAVE), null);
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("name", "alice");
        bus.publish(EventEnvelope.now(EventType.PLAYER_JOIN, payload));

        EventBus.Subscription join = bus.get(joinSub).orElseThrow();
        EventBus.Subscription leave = bus.get(leaveSub).orElseThrow();
        assertEquals(1, join.bufferSize());
        assertEquals(0, leave.bufferSize());
        List<EventEnvelope> drained = join.drain(10);
        assertEquals(1, drained.size());
        assertEquals(EventType.PLAYER_JOIN, drained.get(0).type());
    }

    @Test
    void filterAcceptsMatchingPayload() {
        EventBus bus = new EventBus(16);
        JsonNode uuid = JsonNodeFactory.instance.textNode("u-1");
        String subId =
                bus.subscribe(
                        EnumSet.of(EventType.PLAYER_CHAT),
                        Map.of("player_uuid", uuid));

        ObjectNode matching = JsonNodeFactory.instance.objectNode();
        matching.put("player_uuid", "u-1");
        matching.put("text", "hi");
        bus.publish(EventEnvelope.now(EventType.PLAYER_CHAT, matching));

        ObjectNode notMatching = JsonNodeFactory.instance.objectNode();
        notMatching.put("player_uuid", "u-2");
        bus.publish(EventEnvelope.now(EventType.PLAYER_CHAT, notMatching));

        ObjectNode missingKey = JsonNodeFactory.instance.objectNode();
        missingKey.put("text", "no uuid");
        bus.publish(EventEnvelope.now(EventType.PLAYER_CHAT, missingKey));

        bus.publish(EventEnvelope.now(EventType.PLAYER_CHAT, null));

        EventBus.Subscription s = bus.get(subId).orElseThrow();
        assertEquals(1, s.bufferSize());
    }

    @Test
    void droppedCountIncrementsWhenBufferFull() {
        EventBus bus = new EventBus(16);
        String subId = bus.subscribe(EnumSet.of(EventType.SERVER_TICK), null);
        for (int i = 0; i < 20; i++) {
            bus.publish(EventEnvelope.now(EventType.SERVER_TICK, null));
        }
        EventBus.Subscription s = bus.get(subId).orElseThrow();
        assertEquals(16, s.bufferSize());
        assertEquals(4L, s.droppedCount());
    }

    @Test
    void drainRemovesEntriesUpToMax() {
        EventBus bus = new EventBus(16);
        String subId = bus.subscribe(EnumSet.of(EventType.SERVER_TICK), null);
        for (int i = 0; i < 8; i++) {
            bus.publish(EventEnvelope.now(EventType.SERVER_TICK, null));
        }
        EventBus.Subscription s = bus.get(subId).orElseThrow();
        List<EventEnvelope> first = s.drain(3);
        assertEquals(3, first.size());
        List<EventEnvelope> rest = s.drain(Integer.MAX_VALUE);
        assertEquals(5, rest.size());
        assertEquals(0, s.bufferSize());
    }

    @Test
    void subscriptionTypesAndFiltersAreImmutableSnapshots() {
        EventBus bus = new EventBus(16);
        String id = bus.subscribe(EnumSet.of(EventType.SERVER_TICK), null);
        EventBus.Subscription s = bus.get(id).orElseThrow();
        EnumSet<EventType> typesCopy = s.types();
        typesCopy.add(EventType.SERVER_STOPPED);
        // Mutating the returned copy must not affect what the subscription matches.
        bus.publish(EventEnvelope.now(EventType.SERVER_STOPPED, null));
        assertEquals(0, s.bufferSize());
    }

    private static ObjectNode flow(String dim, int x, int y, int z) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("dimension", dim);
        n.put("x", x);
        n.put("y", y);
        n.put("z", z);
        return n;
    }

    @Test
    void hasSubscribersTracksSubscribeAndUnsubscribe() {
        EventBus bus = new EventBus(16);
        assertFalse(bus.hasSubscribers(EventType.FLUID_FLOW));
        String a = bus.subscribe(EnumSet.of(EventType.FLUID_FLOW, EventType.SERVER_TICK), null);
        String b = bus.subscribe(EnumSet.of(EventType.FLUID_FLOW), null);
        assertTrue(bus.hasSubscribers(EventType.FLUID_FLOW));
        assertTrue(bus.hasSubscribers(EventType.SERVER_TICK));
        bus.unsubscribe(a);
        assertTrue(bus.hasSubscribers(EventType.FLUID_FLOW));
        assertFalse(bus.hasSubscribers(EventType.SERVER_TICK));
        bus.unsubscribe(b);
        assertFalse(bus.hasSubscribers(EventType.FLUID_FLOW));
        // A second unsubscribe of the same id must not drive the count negative.
        bus.unsubscribe(b);
        bus.subscribe(EnumSet.of(EventType.FLUID_FLOW), null);
        assertTrue(bus.hasSubscribers(EventType.FLUID_FLOW));
    }

    @Test
    void regionGateDeliversOnlyInsideItsBoxAndDimension() {
        EventBus bus = new EventBus(16);
        var gate = new EventBus.RegionGate(
                EventType.FLUID_FLOW, "minecraft:overworld", 10, 60, 10, 0, 70, 0, 1000);
        String id = bus.subscribe(EnumSet.of(EventType.FLUID_FLOW), Map.of(), gate);
        assertTrue(bus.anyRegionContains(EventType.FLUID_FLOW, "minecraft:overworld", 5, 65, 5));
        assertFalse(bus.anyRegionContains(EventType.FLUID_FLOW, "minecraft:overworld", 11, 65, 5));
        assertFalse(bus.anyRegionContains(EventType.FLUID_FLOW, "minecraft:the_nether", 5, 65, 5));
        bus.publish(EventEnvelope.now(EventType.FLUID_FLOW, flow("minecraft:overworld", 0, 60, 10)));
        bus.publish(EventEnvelope.now(EventType.FLUID_FLOW, flow("minecraft:overworld", 10, 71, 10)));
        bus.publish(EventEnvelope.now(EventType.FLUID_FLOW, flow("minecraft:the_nether", 5, 65, 5)));
        assertEquals(1, bus.get(id).orElseThrow().bufferSize());
    }

    @Test
    void regionGateRateLimitsAndCountsWhatItDrops() {
        EventBus bus = new EventBus(64);
        var gate = new EventBus.RegionGate(
                EventType.FLUID_FLOW, "minecraft:overworld", 0, 0, 0, 100, 100, 100, 5);
        String id = bus.subscribe(EnumSet.of(EventType.FLUID_FLOW), Map.of(), gate);
        for (int i = 0; i < 20; i++) {
            bus.publish(EventEnvelope.now(EventType.FLUID_FLOW, flow("minecraft:overworld", 1, 1, 1)));
        }
        EventBus.Subscription s = bus.get(id).orElseThrow();
        // Burst capacity equals the per-second rate; a 20-event burst well inside one second
        // delivers about 5 and counts the rest.
        assertTrue(s.bufferSize() >= 5 && s.bufferSize() <= 6, "delivered " + s.bufferSize());
        assertEquals(20 - s.bufferSize(), s.rateLimitedCount());
    }

    @Test
    void regionGateLeavesOtherTypesInTheSameSubscriptionAlone() {
        EventBus bus = new EventBus(16);
        var gate = new EventBus.RegionGate(
                EventType.FLUID_FLOW, "minecraft:overworld", 0, 0, 0, 1, 1, 1, 1);
        String id = bus.subscribe(EnumSet.of(EventType.FLUID_FLOW, EventType.SERVER_TICK), Map.of(), gate);
        for (int i = 0; i < 3; i++) {
            bus.publish(EventEnvelope.now(EventType.SERVER_TICK, null));
        }
        assertEquals(3, bus.get(id).orElseThrow().bufferSize());
        assertEquals(0L, bus.get(id).orElseThrow().rateLimitedCount());
    }

    @Test
    void regionGateRejectsNonPositiveRate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EventBus.RegionGate(EventType.FLUID_FLOW, "d", 0, 0, 0, 1, 1, 1, 0));
    }
}
