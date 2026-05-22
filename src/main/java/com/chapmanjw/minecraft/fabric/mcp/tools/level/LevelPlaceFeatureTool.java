package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CommandResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/**
 * Places a vanilla world-generation feature (a configured feature) at a position
 * via {@code /place feature}. This is how terrain gets <em>grown</em> rather than
 * stamped — trees, vegetation, ore veins, geodes, dripstone clusters — so a
 * forested hillside reads as nature, not as a duplicated tree placed block-by-block.
 *
 * <p>A thin, typed wrapper over the vanilla command (which already runs through
 * {@code command_execute}); the value here is a discoverable schema and a clear
 * error when the feature id or position is wrong.
 */
@McpTool(
        name = "level_place_feature",
        description =
                "Grows a vanilla worldgen feature (configured feature) at a position via /place"
                        + " feature — trees, vegetation, ore veins, geodes, dripstone. The way to add"
                        + " natural detail without stamping identical copies. Args: dimension, feature"
                        + " id (e.g. minecraft:fancy_oak, minecraft:ore_iron), position {x,y,z}.")
public final class LevelPlaceFeatureTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object()
                    .required("dimension", Schemas.string("Dimension identifier, e.g. minecraft:overworld"))
                    .required(
                            "feature",
                            Schemas.string(
                                    "Configured feature id, e.g. minecraft:oak, minecraft:fancy_oak,"
                                            + " minecraft:ore_iron, minecraft:forest_flowers"))
                    .required(
                            "position",
                            Schemas.object()
                                    .required("x", Schemas.integer("X"))
                                    .required("y", Schemas.integer("Y"))
                                    .required("z", Schemas.integer("Z"))
                                    .build())
                    .build();

    public LevelPlaceFeatureTool() {
        super("level_place_feature");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        var r = reader(arguments);
        String dim = r.requireString("dimension");
        String feature = r.requireString("feature");
        var pos = r.requireObject("position");
        int x = pos.get("x").asInt();
        int y = pos.get("y").asInt();
        int z = pos.get("z").asInt();
        String cmd =
                String.format(
                        Locale.ROOT,
                        "execute in %s run place feature %s %d %d %d",
                        dim, feature, x, y, z);
        return onMainThread(
                context,
                ignored -> {
                    CommandResult res = context.adapter().commandExecute(cmd);
                    ObjectNode payload = context.mapper().createObjectNode();
                    payload.put("successCount", res.successCount());
                    if (res.error() != null) {
                        payload.put("error", res.error());
                    }
                    ArrayNode out = payload.putArray("output");
                    for (String line : res.output()) {
                        out.add(line);
                    }
                    return ToolResult.ofToon(payload);
                });
    }
}
