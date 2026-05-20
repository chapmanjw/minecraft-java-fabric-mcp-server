package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.GameRuleInfo;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(name = "level_list_game_rules", description = "Lists every defined game rule with its current value.")
public final class LevelListGameRulesTool extends BaseTool {

    private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

    public LevelListGameRulesTool() {
        super("level_list_game_rules");
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
                    var rules = context.adapter().levelListGameRules();
                    ArrayNode arr = context.mapper().createArrayNode();
                    for (GameRuleInfo r : rules) {
                        ObjectNode n = arr.addObject();
                        n.put("name", r.name());
                        n.put("value", r.value());
                        n.put("type", r.type());
                    }
                    return ToolResult.ofToon(arr);
                });
    }
}
