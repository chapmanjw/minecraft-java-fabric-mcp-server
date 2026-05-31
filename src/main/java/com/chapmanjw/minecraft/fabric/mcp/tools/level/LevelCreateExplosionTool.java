package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3d;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(name = "level_create_explosion", description = "Creates an explosion at a position.", admin = true)
public final class LevelCreateExplosionTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object()
                    .required("dimension", Schemas.string("Dimension identifier"))
                    .required("position", Schemas.position3d("World position"))
                    .required("power", Schemas.number("Explosion power (TNT is 4.0)"))
                    .optional("fire", Schemas.bool("Set fires (default false)"))
                    .optional("break_blocks", Schemas.bool("Destroy blocks (default true)"))
                    .build();

    public LevelCreateExplosionTool() {
        super("level_create_explosion");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        var r = reader(arguments);
        String dim = r.requireString("dimension");
        var posN = r.requireObject("position");
        Vec3d pos = new Vec3d(posN.get("x").asDouble(), posN.get("y").asDouble(), posN.get("z").asDouble());
        float power = (float) r.requireDouble("power");
        boolean fire = r.optBoolean("fire", false);
        boolean breakBlocks = r.optBoolean("break_blocks", true);
        return onMainThread(
                context,
                ignored -> {
                    context.adapter().levelCreateExplosion(dim, pos, power, fire, breakBlocks);
                    return ToolResult.ofText("💥");
                });
    }
}
