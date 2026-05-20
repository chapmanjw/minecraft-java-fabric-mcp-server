package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3d;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(name = "level_play_sound", description = "Plays a sound at a position for every player in the dimension.")
public final class LevelPlaySoundTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object()
                    .required("dimension", Schemas.string("Dimension identifier"))
                    .required("position", Schemas.position3d("World position"))
                    .required("sound_id", Schemas.string("Sound identifier (e.g. minecraft:entity.ghast.scream)"))
                    .optional("volume", Schemas.number("Volume (default 1.0)"))
                    .optional("pitch", Schemas.number("Pitch (default 1.0)"))
                    .build();

    public LevelPlaySoundTool() {
        super("level_play_sound");
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
        Vec3d p = new Vec3d(pos.get("x").asDouble(), pos.get("y").asDouble(), pos.get("z").asDouble());
        String sound = r.requireString("sound_id");
        float vol = (float) r.optDouble("volume", 1.0);
        float pitch = (float) r.optDouble("pitch", 1.0);
        return onMainThread(
                context,
                ignored -> {
                    context.adapter().levelPlaySound(dim, p, sound, vol, pitch);
                    return ToolResult.ofText("Played " + sound);
                });
    }
}
