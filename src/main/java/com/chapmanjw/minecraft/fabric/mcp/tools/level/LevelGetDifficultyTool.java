package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(name = "level_get_difficulty", description = "Returns the world-wide difficulty.")
public final class LevelGetDifficultyTool extends BaseTool {

    private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

    public LevelGetDifficultyTool() {
        super("level_get_difficulty");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        return onMainThread(context, ignored -> ToolResult.ofText(context.adapter().levelGetDifficulty()));
    }
}
