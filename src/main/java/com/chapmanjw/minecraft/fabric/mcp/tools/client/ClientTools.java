package com.chapmanjw.minecraft.fabric.mcp.tools.client;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.ClientAccess;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/**
 * Client-only inspection tools — the {@code client} category. These run inside a real,
 * GPU-rendered Minecraft client (see {@code McpClientMod}) and let Claude SEE and INSPECT the
 * world the way a player does: the actual rendered frame, plus client-side perception the
 * headless server cannot provide (crosshair target, view raycast, nearby entities, open screen).
 *
 * <p>They are deliberately read-only. They do not move, aim, or otherwise drive the player —
 * positioning and aiming the camera is done authoritatively from the <em>server</em> surface
 * (e.g. {@code entity_teleport} / {@code /tp x y z yaw pitch}). This keeps the client endpoint a
 * pure inspection surface; full player agency is a separate, future scope.
 *
 * <p>Every tool reaches Minecraft through {@link ToolContext#client()} ({@link ClientAccess}),
 * which is non-null only on the client MCP server. None of these classes import
 * {@code net.minecraft.client.*}; the client coupling lives behind {@code ClientAccessImpl}.
 */
public final class ClientTools {

    private ClientTools() {}

    /** Resolve the client seam or fail clearly if invoked on the server endpoint. */
    private static ClientAccess client(ToolContext context) {
        ClientAccess c = context.client();
        if (c == null) {
            throw new McpException(
                    ErrorCodes.TOOL_HANDLER_ERROR,
                    "Client tools require the client MCP server (minecraft-java-client). This endpoint"
                            + " has no client attached.");
        }
        return c;
    }

    private static ToolResult notInWorld() {
        return ToolResult.ofText(
                        "The client is not in a world (title screen or disconnected). Join a world,"
                                + " then retry.")
                .markError();
    }

    // -------------------------------------------------------------------
    // view_capture
    // -------------------------------------------------------------------
    @McpTool(
            name = "view_capture",
            description =
                    "Capture the local player's CURRENT first-person frame as a PNG you can SEE — the real"
                            + " client render (textures, lighting, sky, fog, water, entities), i.e. what a"
                            + " human at this position/facing sees. Aim/position the player first from the"
                            + " server surface (entity_teleport or /tp x y z yaw pitch), then capture."
                            + " downscale (>=1) shrinks the frame to keep the inline image small."
                            + " close_screen (default true) dismisses any open GUI — notably the pause/Esc"
                            + " menu that opens when the window loses focus — so the shot shows the world;"
                            + " set it false to capture the current GUI instead.",
            readOnly = true)
    public static final class ViewCapture extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .optional(
                                "downscale",
                                Schemas.integerBetween(
                                        "Integer downscale of the captured frame (1 = native window"
                                                + " resolution).",
                                        1,
                                        8))
                        .optional(
                                "close_screen",
                                Schemas.bool(
                                        "Dismiss any open screen (e.g. the focus-loss pause menu) before"
                                                + " capturing so the frame shows the world. Default true."))
                        .build();

        public ViewCapture() {
            super("view_capture");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            ClientAccess c = client(context);
            if (!c.inGame()) {
                return notInWorld();
            }
            var r = reader(arguments);
            int downscale = Math.max(1, r.optInt("downscale", 1));
            boolean closeScreen = r.optBoolean("close_screen", true);
            byte[] png = c.capturePng(downscale, closeScreen);
            if (png == null || png.length == 0) {
                return ToolResult.ofText("No frame available to capture.").markError();
            }
            return ToolResult.create()
                    .addText("First-person frame from the client's local player.")
                    .addImage(png, "image/png");
        }
    }

    // -------------------------------------------------------------------
    // client_status
    // -------------------------------------------------------------------
    @McpTool(
            name = "client_status",
            description =
                    "Local player + session status as the client sees it: in_game, dimension, position,"
                            + " facing (yaw/pitch), health, hunger, held item, and the connected server"
                            + " (or singleplayer). Use to confirm the inspection client is joined and where"
                            + " it stands before capturing.",
            readOnly = true)
    public static final class ClientStatus extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().build();

        public ClientStatus() {
            super("client_status");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            return okToon(client(context).status());
        }
    }

    // -------------------------------------------------------------------
    // sense_crosshair
    // -------------------------------------------------------------------
    @McpTool(
            name = "sense_crosshair",
            description =
                    "What the crosshair currently points at: NONE/MISS, a block (position, face, block id),"
                            + " or an entity (type, name). The client-side equivalent of 'what am I looking"
                            + " at right now'.",
            readOnly = true)
    public static final class SenseCrosshair extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().build();

        public SenseCrosshair() {
            super("sense_crosshair");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            ClientAccess c = client(context);
            if (!c.inGame()) {
                return notInWorld();
            }
            return okToon(c.crosshair());
        }
    }

    // -------------------------------------------------------------------
    // sense_raycast
    // -------------------------------------------------------------------
    @McpTool(
            name = "sense_raycast",
            description =
                    "Raycast from the player's eye along the current facing and report the first hit"
                            + " (block or entity) within reach. Like sense_crosshair but with an explicit"
                            + " distance and fluid option.",
            readOnly = true)
    public static final class SenseRaycast extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .optional("max_distance", Schemas.number("Reach in blocks (default 20)."))
                        .optional(
                                "include_fluids",
                                Schemas.bool("Whether fluid surfaces count as a hit (default false)."))
                        .build();

        public SenseRaycast() {
            super("sense_raycast");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            ClientAccess c = client(context);
            if (!c.inGame()) {
                return notInWorld();
            }
            var r = reader(arguments);
            double maxDistance = r.optDouble("max_distance", 20.0);
            boolean includeFluids = r.optBoolean("include_fluids", false);
            return okToon(c.raycast(maxDistance, includeFluids));
        }
    }

    // -------------------------------------------------------------------
    // sense_entities
    // -------------------------------------------------------------------
    @McpTool(
            name = "sense_entities",
            description =
                    "Entities the client currently renders within a radius of the player, with type, name,"
                            + " position, and distance. Optional type filter (case-insensitive substring of"
                            + " the entity-type id, e.g. 'zombie' or 'minecraft:cow').",
            readOnly = true)
    public static final class SenseEntities extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .optional("radius", Schemas.number("Search radius in blocks (default 16)."))
                        .optional("type", Schemas.string("Entity-type id substring filter."))
                        .build();

        public SenseEntities() {
            super("sense_entities");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            ClientAccess c = client(context);
            if (!c.inGame()) {
                return notInWorld();
            }
            var r = reader(arguments);
            double radius = r.optDouble("radius", 16.0);
            String type = r.optString("type", null);
            return okToon(c.nearbyEntities(radius, type));
        }
    }

    // -------------------------------------------------------------------
    // sense_screen
    // -------------------------------------------------------------------
    @McpTool(
            name = "sense_screen",
            description =
                    "Current GUI state: whether a screen is open (class + title) and, when a container menu"
                            + " other than the inventory is open, a summary of its slot contents. Confirms"
                            + " what UI the player is looking at.",
            readOnly = true)
    public static final class SenseScreen extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().build();

        public SenseScreen() {
            super("sense_screen");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            return okToon(client(context).screen());
        }
    }
}
