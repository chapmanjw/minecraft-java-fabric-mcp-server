package com.chapmanjw.minecraft.fabric.mcp.tools.function;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Vanilla {@code /function} command surface. */
public final class FunctionTools {

    private FunctionTools() {}

    @McpTool(
            name = "function_run",
            description = "Executes a datapack function, optionally as another entity.")
    public static final class Run extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("function_id", Schemas.string("Function identifier"))
                        .optional("as_entity", Schemas.string("Optional UUID to execute as"))
                        .build();

        public Run() {
            super("function_run");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("function_id");
            String as = r.optString("as_entity", null);
            UUID uuid = null;
            if (as != null && !as.isBlank()) {
                try {
                    uuid = UUID.fromString(as);
                } catch (IllegalArgumentException e) {
                    throw new McpException(
                            ErrorCodes.TOOL_INPUT_INVALID,
                            "function_run: invalid UUID '" + as + "'");
                }
            }
            final UUID finalUuid = uuid;
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().functionRun(id, finalUuid) ? "ran" : "failed"));
        }
    }

    @McpTool(
            name = "function_list",
            description = "Lists every loaded function id, optionally filtered by namespace.")
    public static final class ListAll extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .optional("namespace", Schemas.string("Optional namespace filter"))
                        .build();

        public ListAll() {
            super("function_list");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String ns = reader(arguments).optString("namespace", null);
            return onMainThread(
                    context,
                    ignored -> {
                        List<String> ids = context.adapter().functionList(ns);
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (String s : ids) {
                            arr.add(s);
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(
            name = "function_get_definition",
            description = "Returns a textual representation of a loaded function's body.")
    public static final class GetDefinition extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("function_id", Schemas.string("Function identifier")).build();

        public GetDefinition() {
            super("function_get_definition");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String id = reader(arguments).requireString("function_id");
            return onMainThread(
                    context,
                    ignored -> {
                        var opt = context.adapter().functionGetDefinition(id);
                        if (opt.isEmpty()) {
                            throw new McpException(
                                    ErrorCodes.TOOL_HANDLER_ERROR, "Unknown function: " + id);
                        }
                        ObjectNode n = context.mapper().createObjectNode();
                        n.put("function_id", id);
                        n.put("body", opt.get());
                        return ToolResult.ofToon(n);
                    });
        }
    }
}
