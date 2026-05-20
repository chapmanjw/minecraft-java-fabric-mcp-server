package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.DimensionInfo;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(
        name = "level_get_dimension_info",
        description = "Returns dimension type, height range, current time, and core dimension flags. The legacy biomeSource field was removed in v0.2.x; call level_list_biomes_in_dimension instead.")
public final class LevelGetDimensionInfoTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object()
                    .required("dimension", Schemas.string("Dimension identifier (e.g. minecraft:overworld)"))
                    .build();

    public LevelGetDimensionInfoTool() {
        super("level_get_dimension_info");
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
                ignored -> {
                    DimensionInfo d =
                            context.adapter()
                                    .levelGetDimensionInfo(dim)
                                    .orElseThrow(
                                            () ->
                                                    new McpException(
                                                            ErrorCodes.TOOL_HANDLER_ERROR,
                                                            "Unknown dimension: " + dim));
                    ObjectNode payload = context.mapper().createObjectNode();
                    payload.put("id", d.id());
                    payload.put("typeId", d.typeId());
                    payload.put("minY", d.minY());
                    payload.put("maxY", d.maxY());
                    payload.put("timeOfDay", d.timeOfDay());
                    payload.put("hasCeiling", d.hasCeiling());
                    payload.put("ultrawarm", d.ultrawarm());
                    payload.put("piglinSafe", d.piglinSafe());
                    payload.put("natural", d.natural());
                    return ToolResult.ofToon(payload);
                });
    }
}
