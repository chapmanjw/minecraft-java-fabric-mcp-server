package com.chapmanjw.minecraft.fabric.mcp.tools.bossbar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BossbarInfo;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Vanilla {@code /bossbar} command surface. */
public final class BossbarTools {

    private BossbarTools() {}

    private static ObjectNode toJson(ObjectNode n, BossbarInfo b) {
        n.put("id", b.id());
        n.put("name", b.name());
        n.put("value", b.value());
        n.put("max", b.max());
        n.put("color", b.color());
        n.put("style", b.style());
        n.put("visible", b.visible());
        ArrayNode players = n.putArray("players");
        for (UUID u : b.players()) {
            players.add(u.toString());
        }
        return n;
    }

    @McpTool(name = "bossbar_list", description = "Lists every registered custom boss bar.")
    public static final class ListAll extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

        public ListAll() {
            super("bossbar_list");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            return onMainThread(
                    context,
                    ignored -> {
                        List<BossbarInfo> bars = context.adapter().bossbarList();
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (BossbarInfo b : bars) {
                            toJson(arr.addObject(), b);
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(name = "bossbar_add", description = "Creates a new boss bar with the given id and display name.")
    public static final class Add extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("id", Schemas.string("Bossbar identifier"))
                        .required("name", Schemas.string("Display name"))
                        .build();

        public Add() {
            super("bossbar_add");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("id");
            String name = r.requireString("name");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().bossbarAdd(id, name) ? "added" : "failed"));
        }
    }

    @McpTool(name = "bossbar_remove", description = "Removes a boss bar by id.")
    public static final class Remove extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("id", Schemas.string("Bossbar identifier")).build();

        public Remove() {
            super("bossbar_remove");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String id = reader(arguments).requireString("id");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().bossbarRemove(id) ? "removed" : "failed"));
        }
    }

    @McpTool(name = "bossbar_get", description = "Reads a boss bar by id.")
    public static final class Get extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("id", Schemas.string("Bossbar identifier")).build();

        public Get() {
            super("bossbar_get");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String id = reader(arguments).requireString("id");
            return onMainThread(
                    context,
                    ignored ->
                            context.adapter()
                                    .bossbarGet(id)
                                    .map(
                                            b ->
                                                    ToolResult.ofToon(
                                                            toJson(
                                                                    context.mapper().createObjectNode(),
                                                                    b)))
                                    .orElseThrow(
                                            () ->
                                                    new McpException(
                                                            ErrorCodes.TOOL_HANDLER_ERROR,
                                                            "Unknown bossbar: " + id)));
        }
    }

    @McpTool(name = "bossbar_set_value", description = "Sets the current value of a boss bar.")
    public static final class SetValue extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("id", Schemas.string("Bossbar identifier"))
                        .required("value", Schemas.integer("New value"))
                        .build();

        public SetValue() {
            super("bossbar_set_value");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("id");
            int value = r.requireInt("value");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().bossbarSetValue(id, value) ? "set" : "failed"));
        }
    }

    @McpTool(name = "bossbar_set_max", description = "Sets the maximum value of a boss bar.")
    public static final class SetMax extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("id", Schemas.string("Bossbar identifier"))
                        .required("max", Schemas.integer("New maximum"))
                        .build();

        public SetMax() {
            super("bossbar_set_max");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("id");
            int max = r.requireInt("max");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().bossbarSetMax(id, max) ? "set" : "failed"));
        }
    }

    @McpTool(name = "bossbar_set_name", description = "Sets the display name of a boss bar.")
    public static final class SetName extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("id", Schemas.string("Bossbar identifier"))
                        .required("name", Schemas.string("New display name"))
                        .build();

        public SetName() {
            super("bossbar_set_name");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("id");
            String name = r.requireString("name");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().bossbarSetName(id, name) ? "set" : "failed"));
        }
    }

    @McpTool(name = "bossbar_set_color", description = "Sets the color of a boss bar.")
    public static final class SetColor extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("id", Schemas.string("Bossbar identifier"))
                        .required(
                                "color",
                                Schemas.enumOf(
                                        "Color",
                                        "pink",
                                        "blue",
                                        "red",
                                        "green",
                                        "yellow",
                                        "purple",
                                        "white"))
                        .build();

        public SetColor() {
            super("bossbar_set_color");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("id");
            String color = r.requireString("color");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().bossbarSetColor(id, color) ? "set" : "failed"));
        }
    }

    @McpTool(name = "bossbar_set_style", description = "Sets the style of a boss bar.")
    public static final class SetStyle extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("id", Schemas.string("Bossbar identifier"))
                        .required(
                                "style",
                                Schemas.enumOf(
                                        "Style",
                                        "progress",
                                        "notched_6",
                                        "notched_10",
                                        "notched_12",
                                        "notched_20"))
                        .build();

        public SetStyle() {
            super("bossbar_set_style");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("id");
            String style = r.requireString("style");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().bossbarSetStyle(id, style) ? "set" : "failed"));
        }
    }

    @McpTool(name = "bossbar_set_visible", description = "Shows or hides a boss bar.")
    public static final class SetVisible extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("id", Schemas.string("Bossbar identifier"))
                        .required("visible", Schemas.bool("Visible"))
                        .build();

        public SetVisible() {
            super("bossbar_set_visible");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("id");
            boolean visible = r.requireBoolean("visible");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().bossbarSetVisible(id, visible)
                                            ? "set"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "bossbar_set_players",
            description = "Replace the list of players who see this boss bar. Pass an empty array to hide it from everyone.")
    public static final class SetPlayers extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("id", Schemas.string("Bossbar identifier"))
                        .required("player_uuids", Schemas.arrayOf("Player UUID list", Schemas.string()))
                        .build();

        public SetPlayers() {
            super("bossbar_set_players");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("id");
            JsonNode arr = r.requireArray("player_uuids");
            List<UUID> uuids = new ArrayList<>();
            for (JsonNode n : arr) {
                try {
                    uuids.add(UUID.fromString(n.asText()));
                } catch (IllegalArgumentException ex) {
                    throw new McpException(
                            ErrorCodes.TOOL_INPUT_INVALID,
                            "bossbar_set_players: invalid UUID '" + n.asText() + "'");
                }
            }
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().bossbarSetPlayers(id, uuids) ? "set" : "failed"));
        }
    }
}
