package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3d;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(name = "level_lightning_strike", description = "Summons a lightning bolt at a position.")
public final class LevelLightningStrikeTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object()
                    .required("dimension", Schemas.string("Dimension identifier"))
                    .required("position", Schemas.position3d("World position"))
                    .optional(
                            "cosmetic",
                            Schemas.bool("If true, the bolt does no damage and does not set fire."))
                    .build();

    public LevelLightningStrikeTool() {
        super("level_lightning_strike");
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
        boolean cosmetic = r.optBoolean("cosmetic", false);
        return onMainThread(
                context,
                ignored -> {
                    context.adapter().levelLightningStrike(dim, pos, cosmetic);
                    return ToolResult.ofText("⚡");
                });
    }
}
