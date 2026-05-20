package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BiomeInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(
        name = "level_get_biome_at",
        description = "Returns the biome at a given block position.",
        requiredFabricModules = {"fabric-biome-api-v1"})
public final class LevelGetBiomeAtTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object()
                    .required("dimension", Schemas.string("Dimension identifier"))
                    .required(
                            "position",
                            Schemas.object()
                                    .required("x", Schemas.integer("X"))
                                    .required("y", Schemas.integer("Y"))
                                    .required("z", Schemas.integer("Z"))
                                    .build())
                    .build();

    public LevelGetBiomeAtTool() {
        super("level_get_biome_at");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        var r = reader(arguments);
        String dim = r.requireString("dimension");
        var pos = r.requireObject("position");
        Vec3i p = new Vec3i(pos.get("x").asInt(), pos.get("y").asInt(), pos.get("z").asInt());
        return onMainThread(
                context,
                ignored -> {
                    BiomeInfo b =
                            context.adapter()
                                    .levelGetBiomeAt(dim, p)
                                    .orElseThrow(
                                            () ->
                                                    new McpException(
                                                            ErrorCodes.TOOL_HANDLER_ERROR,
                                                            "Position not loaded in " + dim));
                    ObjectNode n = context.mapper().createObjectNode();
                    n.put("id", b.id());
                    n.put("temperature", b.temperature());
                    n.put("downfall", b.downfall());
                    n.put("hasPrecipitation", b.hasPrecipitation());
                    return ToolResult.ofToon(n);
                });
    }
}
