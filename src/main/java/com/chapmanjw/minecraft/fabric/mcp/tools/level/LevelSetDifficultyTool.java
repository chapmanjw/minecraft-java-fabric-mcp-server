package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(name = "level_set_difficulty", description = "Sets the world-wide difficulty.")
public final class LevelSetDifficultyTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object()
                    .required(
                            "difficulty",
                            Schemas.enumOf("Difficulty", "peaceful", "easy", "normal", "hard"))
                    .build();

    public LevelSetDifficultyTool() {
        super("level_set_difficulty");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String d = reader(arguments).requireString("difficulty");
        return onMainThread(
                context,
                ignored -> {
                    context.adapter().levelSetDifficulty(d);
                    return ToolResult.ofText("Difficulty set to " + d);
                });
    }
}
