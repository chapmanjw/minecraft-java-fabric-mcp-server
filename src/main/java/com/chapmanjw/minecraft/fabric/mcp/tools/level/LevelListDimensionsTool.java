package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(
        name = "level_list_dimensions",
        description = "Lists all loaded dimension identifiers (e.g. minecraft:overworld, minecraft:the_nether).")
public final class LevelListDimensionsTool extends BaseTool {

    private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

    public LevelListDimensionsTool() {
        super("level_list_dimensions");
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
                    var list = context.adapter().levelListDimensions();
                    ArrayNode arr = context.mapper().createArrayNode();
                    list.forEach(arr::add);
                    return ToolResult.ofToon(arr);
                });
    }
}
