package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.LevelInfo;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.Jsons;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(
        name = "level_get_info",
        description =
                "Returns dynamic state of one dimension: time, weather, difficulty, default game mode,"
                        + " spawn point.")
public final class LevelGetInfoTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object().required("dimension", Schemas.string("Dimension identifier")).build();

    public LevelGetInfoTool() {
        super("level_get_info");
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
                    LevelInfo info =
                            context.adapter()
                                    .levelGetInfo(dim)
                                    .orElseThrow(
                                            () ->
                                                    new McpException(
                                                            ErrorCodes.TOOL_HANDLER_ERROR,
                                                            "Unknown dimension: " + dim));
                    return ToolResult.ofToon(Jsons.levelInfo(context.mapper(), info));
                });
    }
}
