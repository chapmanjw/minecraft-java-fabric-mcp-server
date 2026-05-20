package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(
        name = "level_get_time",
        description = "Returns the current time of day (in ticks) for a dimension.")
public final class LevelGetTimeTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object().required("dimension", Schemas.string("Dimension identifier")).build();

    public LevelGetTimeTool() {
        super("level_get_time");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String dim = reader(arguments).requireString("dimension");
        return onMainThread(
                context,
                ignored -> ToolResult.ofText(String.valueOf(context.adapter().levelGetTime(dim))));
    }
}
