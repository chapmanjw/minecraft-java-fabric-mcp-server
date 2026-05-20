package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(
        name = "level_set_time",
        description = "Sets the time of day (in ticks) for a dimension. 0..23999 covers one Minecraft day.")
public final class LevelSetTimeTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object()
                    .required("dimension", Schemas.string("Dimension identifier"))
                    .required("time", Schemas.integer("Time of day in ticks"))
                    .build();

    public LevelSetTimeTool() {
        super("level_set_time");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        var r = reader(arguments);
        String dim = r.requireString("dimension");
        long time = r.requireLong("time");
        return onMainThread(
                context,
                ignored -> {
                    context.adapter().levelSetTime(dim, time);
                    return ToolResult.ofText("Set time of " + dim + " to " + time);
                });
    }
}
