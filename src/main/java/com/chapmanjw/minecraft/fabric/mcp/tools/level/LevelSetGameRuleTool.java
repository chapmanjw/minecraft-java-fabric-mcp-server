package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(
        name = "level_set_game_rule",
        description = "Sets a game rule. Value parsing follows vanilla /gamerule semantics.",
        requiredFabricModules = {"fabric-game-rule-api-v1"},
        admin = true)
public final class LevelSetGameRuleTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object()
                    .required("name", Schemas.string("Game rule name"))
                    .required("value", Schemas.string("New value (stringified)"))
                    .build();

    public LevelSetGameRuleTool() {
        super("level_set_game_rule");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        var r = reader(arguments);
        String name = r.requireString("name");
        String value = r.requireString("value");
        return onMainThread(
                context,
                ignored -> {
                    context.adapter().levelSetGameRule(name, value);
                    return ToolResult.ofText("Set " + name + " = " + value);
                });
    }
}
