package com.chapmanjw.minecraft.fabric.mcp.tools.events;

import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chapmanjw.minecraft.fabric.mcp.protocol.EventBus;
import com.chapmanjw.minecraft.fabric.mcp.protocol.EventEnvelope;
import com.chapmanjw.minecraft.fabric.mcp.protocol.EventType;

/**
 * Subscribes to Fabric API events and routes each one through the {@link EventBus}.
 *
 * <p>Called once during {@code SERVER_STARTING} via
 * {@link #install(EventBus, ObjectMapper)}. Each callback runs on the Minecraft main
 * thread, so direct calls into the bus are safe.
 *
 * <p>Server-tick events are rate-limited to one aggregate per 20 server ticks (about
 * once per second) to keep the bus from being flooded.
 */
public final class EventWiring {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft_fabric_mcp/events");
    private static final long TICK_AGGREGATE_INTERVAL = 20L;

    private EventWiring() {}

    public static void install(EventBus bus, ObjectMapper mapper) {
        AtomicLong tickAgg = new AtomicLong();

        ServerLifecycleEvents.SERVER_STARTING.register(
                s -> bus.publish(EventEnvelope.now(EventType.SERVER_STARTING, mapper.createObjectNode())));
        ServerLifecycleEvents.SERVER_STARTED.register(
                s -> bus.publish(EventEnvelope.now(EventType.SERVER_STARTED, mapper.createObjectNode())));
        ServerLifecycleEvents.SERVER_STOPPING.register(
                s -> bus.publish(EventEnvelope.now(EventType.SERVER_STOPPING, mapper.createObjectNode())));
        ServerLifecycleEvents.SERVER_STOPPED.register(
                s -> bus.publish(EventEnvelope.now(EventType.SERVER_STOPPED, mapper.createObjectNode())));

        ServerTickEvents.END_SERVER_TICK.register(
                s -> {
                    long n = tickAgg.incrementAndGet();
                    if (n % TICK_AGGREGATE_INTERVAL != 0) {
                        return;
                    }
                    ObjectNode payload = mapper.createObjectNode();
                    payload.put("server_tick", n);
                    payload.put("player_count", s.getPlayerCount());
                    bus.publish(EventEnvelope.now(EventType.SERVER_TICK, payload));
                });

        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> publishPlayerEvent(bus, mapper, EventType.PLAYER_JOIN, handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) ->
                        publishPlayerEvent(bus, mapper, EventType.PLAYER_LEAVE, handler.player));

        ServerMessageEvents.CHAT_MESSAGE.register(
                (message, sender, params) -> {
                    ObjectNode payload = mapper.createObjectNode();
                    payload.put("player_uuid", sender.getUUID().toString());
                    payload.put("name", sender.getName().getString());
                    payload.put("message", message.signedContent());
                    bus.publish(EventEnvelope.now(EventType.PLAYER_CHAT, payload));
                });

        AttackBlockCallback.EVENT.register(
                (player, world, hand, pos, direction) -> {
                    if (player instanceof ServerPlayer sp) {
                        ObjectNode payload = mapper.createObjectNode();
                        payload.put("player_uuid", sp.getUUID().toString());
                        payload.put("dimension", sp.level().dimension().identifier().toString());
                        ObjectNode p = payload.putObject("position");
                        p.put("x", pos.getX());
                        p.put("y", pos.getY());
                        p.put("z", pos.getZ());
                        bus.publish(EventEnvelope.now(EventType.BLOCK_BREAK, payload));
                    }
                    return net.minecraft.world.InteractionResult.PASS;
                });

        UseBlockCallback.EVENT.register(
                (player, world, hand, hitResult) -> {
                    if (player instanceof ServerPlayer sp) {
                        BlockPos pos = hitResult.getBlockPos();
                        ObjectNode payload = mapper.createObjectNode();
                        payload.put("player_uuid", sp.getUUID().toString());
                        payload.put("dimension", sp.level().dimension().identifier().toString());
                        ObjectNode p = payload.putObject("position");
                        p.put("x", pos.getX());
                        p.put("y", pos.getY());
                        p.put("z", pos.getZ());
                        bus.publish(EventEnvelope.now(EventType.BLOCK_USE, payload));
                    }
                    return net.minecraft.world.InteractionResult.PASS;
                });

        LOGGER.info("EventWiring installed — Minecraft events will be routed to the MCP event bus");
    }

    private static void publishPlayerEvent(
            EventBus bus, ObjectMapper mapper, EventType type, ServerPlayer p) {
        if (p == null) {
            return;
        }
        ObjectNode payload = mapper.createObjectNode();
        payload.put("player_uuid", p.getUUID().toString());
        payload.put("name", p.getName().getString());
        payload.put("dimension", p.level().dimension().identifier().toString());
        ObjectNode pos = payload.putObject("position");
        pos.put("x", p.getX());
        pos.put("y", p.getY());
        pos.put("z", p.getZ());
        bus.publish(EventEnvelope.now(type, payload));
    }
}
