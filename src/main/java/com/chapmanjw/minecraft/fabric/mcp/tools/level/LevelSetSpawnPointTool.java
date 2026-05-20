package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(name = "level_set_spawn_point", description = "Sets the world spawn point for a dimension.")
public final class LevelSetSpawnPointTool extends BaseTool {

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

    public LevelSetSpawnPointTool() {
        super("level_set_spawn_point");
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
                    context.adapter().levelSetSpawnPoint(dim, p);
                    return ToolResult.ofText("Spawn set to (" + p.x() + "," + p.y() + "," + p.z() + ")");
                });
    }
}
