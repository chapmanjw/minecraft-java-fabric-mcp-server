package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.Jsons;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(name = "level_get_spawn_point", description = "Returns the world spawn point for a dimension.")
public final class LevelGetSpawnPointTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object().required("dimension", Schemas.string("Dimension identifier")).build();

    public LevelGetSpawnPointTool() {
        super("level_get_spawn_point");
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
                    Vec3i pos = context.adapter().levelGetSpawnPoint(dim);
                    return ToolResult.ofToon(Jsons.vec3i(context.mapper(), pos));
                });
    }
}
