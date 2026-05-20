package com.chapmanjw.minecraft.fabric.mcp.tools.advancement;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.AdvancementProgressInfo;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Vanilla advancement command surface. */
public final class AdvancementTools {

    private AdvancementTools() {}

    private static UUID parseUuid(String s, String tool) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new McpException(
                    ErrorCodes.TOOL_INPUT_INVALID, tool + ": invalid UUID '" + s + "'");
        }
    }

    private static JsonNode modeEnum() {
        return Schemas.enumOf("Grant/revoke mode", "everything", "only", "until", "through", "from");
    }

    @McpTool(
            name = "advancement_grant",
            description = "Grants an advancement (or criterion) to a player.")
    public static final class Grant extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("player_uuid", Schemas.string("Player UUID"))
                        .required("advancement_id", Schemas.string("Advancement identifier"))
                        .required("mode", modeEnum())
                        .optional("criterion", Schemas.string("Criterion name (only mode)"))
                        .build();

        public Grant() {
            super("advancement_grant");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = parseUuid(r.requireString("player_uuid"), toolName);
            String id = r.requireString("advancement_id");
            String mode = r.requireString("mode");
            String criterion = r.optString("criterion", null);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().advancementGrant(uuid, id, mode, criterion)
                                            ? "granted"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "advancement_revoke",
            description = "Revokes an advancement (or criterion) from a player.")
    public static final class Revoke extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("player_uuid", Schemas.string("Player UUID"))
                        .required("advancement_id", Schemas.string("Advancement identifier"))
                        .required("mode", modeEnum())
                        .optional("criterion", Schemas.string("Criterion name (only mode)"))
                        .build();

        public Revoke() {
            super("advancement_revoke");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = parseUuid(r.requireString("player_uuid"), toolName);
            String id = r.requireString("advancement_id");
            String mode = r.requireString("mode");
            String criterion = r.optString("criterion", null);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().advancementRevoke(uuid, id, mode, criterion)
                                            ? "revoked"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "advancement_list_player",
            description = "Returns granted and in-progress advancements for a player.")
    public static final class ListPlayer extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("player_uuid", Schemas.string("Player UUID")).build();

        public ListPlayer() {
            super("advancement_list_player");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            UUID uuid = parseUuid(reader(arguments).requireString("player_uuid"), toolName);
            return onMainThread(
                    context,
                    ignored -> {
                        AdvancementProgressInfo info = context.adapter().advancementListPlayer(uuid);
                        ObjectNode n = context.mapper().createObjectNode();
                        ArrayNode granted = n.putArray("granted");
                        for (String s : info.granted()) {
                            granted.add(s);
                        }
                        ArrayNode inProgress = n.putArray("in_progress");
                        for (AdvancementProgressInfo.InProgress p : info.inProgress()) {
                            ObjectNode entry = inProgress.addObject();
                            entry.put("id", p.id());
                            ArrayNode done = entry.putArray("criteria_completed");
                            for (String c : p.criteriaCompleted()) {
                                done.add(c);
                            }
                            ArrayNode rem = entry.putArray("criteria_remaining");
                            for (String c : p.criteriaRemaining()) {
                                rem.add(c);
                            }
                        }
                        return ToolResult.ofToon(n);
                    });
        }
    }

    @McpTool(name = "advancement_list_all", description = "Lists every advancement id from the registry.")
    public static final class ListAll extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

        public ListAll() {
            super("advancement_list_all");
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
                        List<String> ids = context.adapter().advancementListAll();
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (String s : ids) {
                            arr.add(s);
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(
            name = "advancement_get_definition",
            description = "Returns the JSON definition of an advancement.")
    public static final class GetDefinition extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("advancement_id", Schemas.string("Advancement id")).build();

        public GetDefinition() {
            super("advancement_get_definition");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String id = reader(arguments).requireString("advancement_id");
            return onMainThread(
                    context,
                    ignored -> {
                        var opt = context.adapter().advancementGetDefinition(id);
                        if (opt.isEmpty()) {
                            throw new McpException(
                                    ErrorCodes.TOOL_HANDLER_ERROR, "Unknown advancement: " + id);
                        }
                        ObjectNode n = context.mapper().createObjectNode();
                        n.put("advancement_id", id);
                        n.put("definition", opt.get());
                        return ToolResult.ofToon(n);
                    });
        }
    }
}
