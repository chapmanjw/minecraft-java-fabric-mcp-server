package com.chapmanjw.minecraft.fabric.mcp.tools.playerscreen;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Open and close menu screens for a server player. */
public final class PlayerScreenTools {

    private PlayerScreenTools() {}

    private static UUID parseUuid(String s, String tool) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new McpException(
                    ErrorCodes.TOOL_INPUT_INVALID, tool + ": invalid UUID '" + s + "'");
        }
    }

    @McpTool(
            name = "player_screen_open_menu",
            description =
                    "Opens a standard menu screen for the player (anvil, crafting_table, etc.).",
            requiredFabricModules = {"fabric-screen-handler-api-v1"})
    public static final class OpenMenu extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", Schemas.string("Player UUID"))
                        .required(
                                "menu_type",
                                Schemas.enumOf(
                                        "Menu type",
                                        "anvil",
                                        "crafting_table",
                                        "enchanting_table",
                                        "loom",
                                        "stonecutter",
                                        "grindstone",
                                        "smithing_table",
                                        "cartography_table"))
                        .optional("title", Schemas.string("Optional title"))
                        .build();

        public OpenMenu() {
            super("player_screen_open_menu");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = parseUuid(r.requireString("uuid"), toolName);
            String type = r.requireString("menu_type");
            String title = r.optString("title", null);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerScreenOpenMenu(uuid, type, title)
                                            ? "opened"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "player_screen_open_container",
            description =
                    "Opens the block container at the given position for the player (chest, barrel, etc.).",
            requiredFabricModules = {"fabric-screen-handler-api-v1"})
    public static final class OpenContainer extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", Schemas.string("Player UUID"))
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required(
                                "position",
                                Schemas.object()
                                        .required("x", Schemas.integer("X"))
                                        .required("y", Schemas.integer("Y"))
                                        .required("z", Schemas.integer("Z"))
                                        .build())
                        .build();

        public OpenContainer() {
            super("player_screen_open_container");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = parseUuid(r.requireString("uuid"), toolName);
            String dim = r.requireString("dimension");
            var pos = r.requireObject("position");
            Vec3i p = new Vec3i(pos.get("x").asInt(), pos.get("y").asInt(), pos.get("z").asInt());
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerScreenOpenContainer(uuid, dim, p)
                                            ? "opened"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "player_screen_close",
            description = "Closes whatever screen the player currently has open.",
            requiredFabricModules = {"fabric-screen-handler-api-v1"})
    public static final class Close extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("uuid", Schemas.string("Player UUID")).build();

        public Close() {
            super("player_screen_close");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            UUID uuid = parseUuid(reader(arguments).requireString("uuid"), toolName);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerScreenClose(uuid) ? "closed" : "failed"));
        }
    }
}
