package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(
        name = "level_get_weather",
        description = "Returns the current weather (one of: clear, rain, thunder) for a dimension.")
public final class LevelGetWeatherTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object().required("dimension", Schemas.string("Dimension identifier")).build();

    public LevelGetWeatherTool() {
        super("level_get_weather");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String dim = reader(arguments).requireString("dimension");
        return onMainThread(context, ignored -> ToolResult.ofText(context.adapter().levelGetWeather(dim)));
    }
}
