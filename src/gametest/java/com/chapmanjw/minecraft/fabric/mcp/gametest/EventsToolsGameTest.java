package com.chapmanjw.minecraft.fabric.mcp.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.chapmanjw.minecraft.fabric.mcp.protocol.EventEnvelope;
import com.chapmanjw.minecraft.fabric.mcp.protocol.EventType;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/** Event-domain gametests — subscribe, publish, poll. */
public final class EventsToolsGameTest implements FabricGameTest {

    @GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")
    public void subscribe_then_poll_returns_published_events(GameTestHelper helper) {
        var b = GametestHarness.bootstrap(helper.getLevel().getServer());

        // 1) Subscribe to player.chat events.
        var subArgs = b.mapper().createObjectNode();
        var types = subArgs.putArray("event_types");
        types.add("player.chat");
        var subResult = GametestHarness.callTool(b.dispatcher(), "events_subscribe", subArgs);
        String subId = subResult.path("structuredContent").path("subscription_id").asText();
        helper.assertTrue(subId.length() == 36, "subscription_id should be a UUID");

        // 2) Synthesize an event via the bus directly.
        var payload = JsonNodeFactory.instance.objectNode();
        payload.put("name", "gametest");
        payload.put("message", "hello");
        b.context().eventBus().publish(EventEnvelope.now(EventType.PLAYER_CHAT, payload));

        // 3) Poll and verify.
        var pollArgs = b.mapper().createObjectNode();
        pollArgs.put("subscription_id", subId);
        var pollResult = GametestHarness.callTool(b.dispatcher(), "events_poll", pollArgs);
        int eventCount = pollResult.path("structuredContent").path("events").size();
        helper.assertTrue(eventCount == 1, "expected 1 event, got " + eventCount);

        // 4) Cleanup.
        var unsubArgs = b.mapper().createObjectNode();
        unsubArgs.put("subscription_id", subId);
        GametestHarness.callTool(b.dispatcher(), "events_unsubscribe", unsubArgs);

        helper.succeed();
    }
}
