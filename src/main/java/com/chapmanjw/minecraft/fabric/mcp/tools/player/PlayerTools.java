package com.chapmanjw.minecraft.fabric.mcp.tools.player;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.InventoryInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ItemSpec;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.PlayerInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.Jsons;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Player tools. */
public final class PlayerTools {

    private PlayerTools() {}

    private static UUID readUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Invalid UUID: " + s);
        }
    }

    @McpTool(name = "player_list_online", description = "Lists every connected player with their position and stats.")
    public static final class ListOnline extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

        public ListOnline() {
            super("player_list_online");
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
                        List<PlayerInfo> players = context.adapter().playerListOnline();
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (PlayerInfo p : players) {
                            arr.add(Jsons.player(context.mapper(), p));
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(name = "player_get_info", description = "Returns full state for one player.")
    public static final class GetInfo extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("uuid", Schemas.string("Player UUID")).build();

        public GetInfo() {
            super("player_get_info");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            UUID uuid = readUuid(reader(arguments).requireString("uuid"));
            return onMainThread(
                    context,
                    ignored -> {
                        PlayerInfo info =
                                context.adapter()
                                        .playerGetInfo(uuid)
                                        .orElseThrow(
                                                () ->
                                                        new McpException(
                                                                ErrorCodes.TOOL_HANDLER_ERROR,
                                                                "Player not online: " + uuid));
                        return ToolResult.ofToon(Jsons.player(context.mapper(), info));
                    });
        }
    }

    @McpTool(name = "player_get_inventory", description = "Returns the contents of the player's main inventory.")
    public static final class GetInventory extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("uuid", Schemas.string("Player UUID")).build();

        public GetInventory() {
            super("player_get_inventory");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            UUID uuid = readUuid(reader(arguments).requireString("uuid"));
            return onMainThread(
                    context,
                    ignored -> {
                        InventoryInfo inv =
                                context.adapter()
                                        .playerGetInventory(uuid)
                                        .orElseThrow(
                                                () ->
                                                        new McpException(
                                                                ErrorCodes.TOOL_HANDLER_ERROR,
                                                                "Player not online: " + uuid));
                        return ToolResult.ofToon(Jsons.inventory(context.mapper(), inv));
                    });
        }
    }

    @McpTool(name = "player_give_item", description = "Gives an item stack to a player.")
    public static final class GiveItem extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", Schemas.string("Player UUID"))
                        .required(
                                "item",
                                Schemas.object()
                                        .required("id", Schemas.string("Item id"))
                                        .optional("count", Schemas.integer("Count (default 1)"))
                                        .optional("components", Schemas.string("Components SNBT (optional)"))
                                        .build())
                        .build();

        public GiveItem() {
            super("player_give_item");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid(r.requireString("uuid"));
            JsonNode itemNode = r.requireObject("item");
            String id = itemNode.get("id").asText();
            int count = itemNode.has("count") ? itemNode.get("count").asInt() : 1;
            String comps = itemNode.has("components") ? itemNode.get("components").asText() : null;
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                                    .playerGiveItem(uuid, new ItemSpec(id, count, comps))
                                            ? "given"
                                            : "failed"));
        }
    }

    @McpTool(name = "player_clear_inventory_slot", description = "Clears a single inventory slot.")
    public static final class ClearSlot extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", Schemas.string("Player UUID"))
                        .required("slot", Schemas.integer("Slot index"))
                        .build();

        public ClearSlot() {
            super("player_clear_inventory_slot");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid(r.requireString("uuid"));
            int slot = r.requireInt("slot");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerClearInventorySlot(uuid, slot) ? "cleared" : "failed"));
        }
    }

    @McpTool(name = "player_clear_all_inventory", description = "Clears the player's entire inventory.")
    public static final class ClearAll extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("uuid", Schemas.string("Player UUID")).build();

        public ClearAll() {
            super("player_clear_all_inventory");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            UUID uuid = readUuid(reader(arguments).requireString("uuid"));
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerClearAllInventory(uuid) ? "cleared" : "failed"));
        }
    }

    @McpTool(name = "player_set_gamemode", description = "Sets the player's game mode.")
    public static final class SetGamemode extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", Schemas.string("Player UUID"))
                        .required(
                                "gamemode",
                                Schemas.enumOf(
                                        "Game mode", "survival", "creative", "adventure", "spectator"))
                        .build();

        public SetGamemode() {
            super("player_set_gamemode");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid(r.requireString("uuid"));
            String mode = r.requireString("gamemode");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerSetGamemode(uuid, mode) ? "set" : "failed"));
        }
    }

    @McpTool(name = "player_kick", description = "Disconnects a player with an optional reason.", admin = true)
    public static final class Kick extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", Schemas.string("Player UUID"))
                        .optional("reason", Schemas.string("Kick reason"))
                        .build();

        public Kick() {
            super("player_kick");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid(r.requireString("uuid"));
            String reason = r.optString("reason", "Kicked by MCP");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerKick(uuid, reason) ? "kicked" : "player not found"));
        }
    }

    @McpTool(name = "player_send_message", description = "Sends a chat message to a single player.")
    public static final class SendMessage extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", Schemas.string("Player UUID"))
                        .required("message", Schemas.string("Message body"))
                        .build();

        public SendMessage() {
            super("player_send_message");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid(r.requireString("uuid"));
            String msg = r.requireString("message");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerSendMessage(uuid, msg) ? "sent" : "player not found"));
        }
    }

    @McpTool(name = "player_send_actionbar", description = "Shows a text bar above the hotbar of one player.")
    public static final class SendActionbar extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", Schemas.string("Player UUID"))
                        .required("message", Schemas.string("Actionbar text"))
                        .build();

        public SendActionbar() {
            super("player_send_actionbar");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid(r.requireString("uuid"));
            String msg = r.requireString("message");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerSendActionbar(uuid, msg) ? "shown" : "failed"));
        }
    }

    @McpTool(name = "player_send_title", description = "Shows a title (with optional subtitle and fade timing).")
    public static final class SendTitle extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", Schemas.string("Player UUID"))
                        .required("title", Schemas.string("Main title"))
                        .optional("subtitle", Schemas.string("Subtitle"))
                        .optional("fade_in_ticks", Schemas.integer("Fade in (default 10)"))
                        .optional("stay_ticks", Schemas.integer("Stay (default 70)"))
                        .optional("fade_out_ticks", Schemas.integer("Fade out (default 20)"))
                        .build();

        public SendTitle() {
            super("player_send_title");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid(r.requireString("uuid"));
            String title = r.requireString("title");
            String subtitle = r.optString("subtitle", null);
            int fadeIn = r.optInt("fade_in_ticks", 10);
            int stay = r.optInt("stay_ticks", 70);
            int fadeOut = r.optInt("fade_out_ticks", 20);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                                    .playerSendTitle(
                                                            uuid, title, subtitle, fadeIn, stay, fadeOut)
                                            ? "shown"
                                            : "failed"));
        }
    }

    @McpTool(name = "player_play_sound", description = "Plays a sound for a single player.")
    public static final class PlaySound extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", Schemas.string("Player UUID"))
                        .required("sound_id", Schemas.string("Sound id"))
                        .optional("volume", Schemas.number("Volume (default 1.0)"))
                        .optional("pitch", Schemas.number("Pitch (default 1.0)"))
                        .build();

        public PlaySound() {
            super("player_play_sound");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid(r.requireString("uuid"));
            String sound = r.requireString("sound_id");
            float vol = (float) r.optDouble("volume", 1.0);
            float pitch = (float) r.optDouble("pitch", 1.0);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerPlaySound(uuid, sound, vol, pitch)
                                            ? "played"
                                            : "failed"));
        }
    }

    @McpTool(name = "player_set_spawn_point", description = "Sets the player's individual spawn point.")
    public static final class SetSpawn extends BaseTool {
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

        public SetSpawn() {
            super("player_set_spawn_point");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid(r.requireString("uuid"));
            String dim = r.requireString("dimension");
            JsonNode pos = r.requireObject("position");
            Vec3i p = new Vec3i(pos.get("x").asInt(), pos.get("y").asInt(), pos.get("z").asInt());
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerSetSpawnPoint(uuid, dim, p) ? "set" : "failed"));
        }
    }

    @McpTool(name = "player_grant_xp", description = "Awards experience points to a player.")
    public static final class GrantXp extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", Schemas.string("Player UUID"))
                        .required("amount", Schemas.integer("Experience points to add"))
                        .build();

        public GrantXp() {
            super("player_grant_xp");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid(r.requireString("uuid"));
            int amount = r.requireInt("amount");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerGrantXp(uuid, amount) ? "granted" : "failed"));
        }
    }

    @McpTool(name = "player_set_xp_level", description = "Sets the player's experience level.")
    public static final class SetXpLevel extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", Schemas.string("Player UUID"))
                        .required("level", Schemas.integer("New experience level"))
                        .build();

        public SetXpLevel() {
            super("player_set_xp_level");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid(r.requireString("uuid"));
            int level = r.requireInt("level");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerSetXpLevel(uuid, level) ? "set" : "failed"));
        }
    }

    @McpTool(
            name = "player_set_camera",
            description = "Spectator camera — view through another entity (vanilla /spectate).")
    public static final class SetCamera extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("viewer", Schemas.string("Viewer player UUID"))
                        .required("target", Schemas.string("Target entity UUID"))
                        .build();

        public SetCamera() {
            super("player_set_camera");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID viewer = readUuid(r.requireString("viewer"));
            UUID target = readUuid(r.requireString("target"));
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().playerSetCamera(viewer, target) ? "set" : "failed"));
        }
    }
}
