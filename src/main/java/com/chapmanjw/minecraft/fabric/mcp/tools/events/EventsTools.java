package com.chapmanjw.minecraft.fabric.mcp.tools.events;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.EventBus;
import com.chapmanjw.minecraft.fabric.mcp.protocol.EventEnvelope;
import com.chapmanjw.minecraft.fabric.mcp.protocol.EventType;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Event-subscription tools. */
public final class EventsTools {

    private EventsTools() {}

    private static EnumSet<EventType> parseTypes(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            throw new McpException(
                    ErrorCodes.TOOL_INPUT_INVALID,
                    "events_subscribe: 'event_types' must be a non-empty string array");
        }
        EnumSet<EventType> set = EnumSet.noneOf(EventType.class);
        for (JsonNode n : arr) {
            String name = n.asText();
            EventType type =
                    EventType.fromWireName(name)
                            .orElseThrow(
                                    () ->
                                            new McpException(
                                                    ErrorCodes.TOOL_INPUT_INVALID,
                                                    "Unknown event type: " + name));
            set.add(type);
        }
        return set;
    }

    private static ObjectNode toJson(ObjectNode dest, EventBus.Subscription s) {
        dest.put("subscription_id", s.id());
        ArrayNode types = dest.putArray("event_types");
        for (EventType t : s.types()) {
            types.add(t.wireName());
        }
        ObjectNode filt = dest.putObject("filters");
        for (Map.Entry<String, JsonNode> e : s.filters().entrySet()) {
            filt.set(e.getKey(), e.getValue());
        }
        dest.put("buffer_size", s.bufferSize());
        dest.put("dropped_count", s.droppedCount());
        return dest;
    }

    @McpTool(
            name = "events_subscribe",
            description =
                    "Registers a subscription for one or more event types with optional filters."
                            + " Returns an opaque subscription_id for events_poll / events_unsubscribe.",
            requiredFabricModules = {
                    "fabric-lifecycle-events-v1",
                    "fabric-message-api-v1",
                    "fabric-networking-api-v1",
                    "fabric-events-interaction-v0"
            })
    public static final class Subscribe extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required(
                                "event_types",
                                Schemas.arrayOf(
                                        "Event type wire names using dot notation (e.g. \"player.chat\", \"block.place\", \"entity.death\"). See docs/tools.md for the full list.",
                                        Schemas.string()))
                        .optional(
                                "filters",
                                Schemas.object()
                                        .description("Optional per-event-type filter map")
                                        .allowAdditional()
                                        .build())
                        .build();

        public Subscribe() {
            super("events_subscribe");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            EnumSet<EventType> types = parseTypes(r.requireArray("event_types"));
            Map<String, JsonNode> filters = new HashMap<>();
            JsonNode f = r.optObject("filters");
            if (f != null) {
                f.fields().forEachRemaining(e -> filters.put(e.getKey(), e.getValue()));
            }
            String id = context.eventBus().subscribe(types, filters);
            ObjectNode result = context.mapper().createObjectNode();
            result.put("subscription_id", id);
            return ToolResult.ofToon(result);
        }
    }

    @McpTool(
            name = "events_poll",
            description = "Drains pending events for a subscription. Use a max value to limit batch size.")
    public static final class Poll extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("subscription_id", Schemas.string("Subscription id"))
                        .optional("max", Schemas.integerBetween("Max events to drain", 1, 65536))
                        .build();

        public Poll() {
            super("events_poll");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("subscription_id");
            int max = r.optInt("max", 256);
            EventBus.Subscription sub =
                    context.eventBus()
                            .get(id)
                            .orElseThrow(
                                    () ->
                                            new McpException(
                                                    ErrorCodes.TOOL_HANDLER_ERROR,
                                                    "Unknown subscription_id: " + id));
            List<EventEnvelope> events = sub.drain(max);
            ObjectNode payload = context.mapper().createObjectNode();
            ArrayNode arr = payload.putArray("events");
            for (EventEnvelope e : events) {
                ObjectNode n = arr.addObject();
                n.put("type", e.type().wireName());
                n.put("timestamp", e.timestamp().toString());
                if (e.payload() != null) {
                    n.set("payload", e.payload());
                }
            }
            payload.put("dropped_count", sub.droppedCount());
            payload.put("buffer_size", sub.bufferSize());
            return ToolResult.ofToon(payload);
        }
    }

    @McpTool(name = "events_list_subscriptions", description = "Lists every active subscription.")
    public static final class ListSubscriptions extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

        public ListSubscriptions() {
            super("events_list_subscriptions");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            List<EventBus.Subscription> subs = context.eventBus().list();
            ArrayNode arr = context.mapper().createArrayNode();
            for (EventBus.Subscription s : subs) {
                toJson(arr.addObject(), s);
            }
            return ToolResult.ofToon(arr);
        }
    }

    @McpTool(name = "events_unsubscribe", description = "Removes an event subscription.")
    public static final class Unsubscribe extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("subscription_id", Schemas.string("Subscription id")).build();

        public Unsubscribe() {
            super("events_unsubscribe");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String id = reader(arguments).requireString("subscription_id");
            boolean removed = context.eventBus().unsubscribe(id);
            return ToolResult.ofText(removed ? "unsubscribed " + id : "not found");
        }
    }

    /**
     * Internal sentinel — returns the current server time for synthetic events such as
     * tick aggregates. Kept here so the event handler module has a single place to
     * mint timestamps.
     */
    public static Instant now() {
        return Instant.now();
    }
}
