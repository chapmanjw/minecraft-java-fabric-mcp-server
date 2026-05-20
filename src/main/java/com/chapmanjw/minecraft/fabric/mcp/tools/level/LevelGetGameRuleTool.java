package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.GameRuleInfo;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(name = "level_get_game_rule", description = "Returns the value of a single game rule.")
public final class LevelGetGameRuleTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object().required("name", Schemas.string("Game rule name (e.g. doDaylightCycle)")).build();

    public LevelGetGameRuleTool() {
        super("level_get_game_rule");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String name = reader(arguments).requireString("name");
        return onMainThread(
                context,
                ignored -> {
                    GameRuleInfo info =
                            context.adapter()
                                    .levelGetGameRule(name)
                                    .orElseThrow(
                                            () ->
                                                    new McpException(
                                                            ErrorCodes.TOOL_HANDLER_ERROR,
                                                            "Unknown game rule: " + name));
                    ObjectNode n = context.mapper().createObjectNode();
                    n.put("name", info.name());
                    n.put("value", info.value());
                    n.put("type", info.type());
                    return ToolResult.ofToon(n);
                });
    }
}
