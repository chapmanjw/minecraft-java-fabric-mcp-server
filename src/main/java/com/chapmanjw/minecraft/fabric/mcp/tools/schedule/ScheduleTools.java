package com.chapmanjw.minecraft.fabric.mcp.tools.schedule;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ScheduledFunctionInfo;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Vanilla {@code /schedule} command surface. */
public final class ScheduleTools {

    private ScheduleTools() {}

    @McpTool(
            name = "schedule_function",
            description = "Schedules a function to run after the given number of ticks.")
    public static final class FunctionTool extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("function_id", Schemas.string("Function identifier"))
                        .required("ticks", Schemas.integer("Ticks until execution"))
                        .required("mode", Schemas.enumOf("Conflict mode", "append", "replace"))
                        .build();

        public FunctionTool() {
            super("schedule_function");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("function_id");
            int ticks = r.requireInt("ticks");
            String mode = r.requireString("mode");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().scheduleFunction(id, ticks, mode)
                                            ? "scheduled"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "schedule_clear",
            description = "Clears any pending schedule entries for a function.")
    public static final class Clear extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("function_id", Schemas.string("Function identifier")).build();

        public Clear() {
            super("schedule_clear");
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
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().scheduleClear(id) ? "cleared" : "failed"));
        }
    }

    @McpTool(name = "schedule_list", description = "Lists pending scheduled function entries.")
    public static final class ListAll extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

        public ListAll() {
            super("schedule_list");
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
                        List<ScheduledFunctionInfo> entries = context.adapter().scheduleList();
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (ScheduledFunctionInfo entry : entries) {
                            ObjectNode n = arr.addObject();
                            n.put("function_id", entry.functionId());
                            n.put("ticks_remaining", entry.ticksRemaining());
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }
}
