package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CommandResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
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
                        + " id (e.g. minecraft:fancy_oak, minecraft:ore_iron), position {x,y,z}. On"
                        + " Minecraft 26.3+ pass definition (a configured-feature JSON object, 26.3"
                        + " format) instead of feature to place a custom feature with no datapack.")
public final class LevelPlaceFeatureTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object()
                    .required("dimension", Schemas.string("Dimension identifier, e.g. minecraft:overworld"))
                    .optional(
                            "feature",
                            Schemas.string(
                                    "Configured feature id, e.g. minecraft:oak, minecraft:fancy_oak,"
                                            + " minecraft:ore_iron, minecraft:forest_flowers. Give this"
                                            + " or definition."))
                    .optional(
                            "definition",
                            Schemas.object()
                                    .description(
                                            "Inline configured feature (Minecraft 26.3+), e.g."
                                                    + " {\"type\":\"minecraft:simple_block\","
                                                    + "\"to_place\":{\"id\":\"minecraft:gold_block\"}}."
                                                    + " Give this or feature.")
                                    .allowAdditional()
                                    .build())
                    .required(
                            "position",
                            Schemas.object()
                                    .required("x", Schemas.integer("X"))
                                    .required("y", Schemas.integer("Y"))
                                    .required("z", Schemas.integer("Z"))
                                    .build())
                    .build();

    // Minecraft 26.3 lets /place feature take a whole configured-feature definition inline
    // (SNBT; a JSON object is valid SNBT) instead of only a registry id. Older targets do not.
    //? if mc_gte_26_3 {
    /*static final boolean INLINE_DEFINITIONS = true;
    *///?} else {
    static final boolean INLINE_DEFINITIONS = false;
    //?}

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
        String feature = featureArgument(arguments.get("feature"), arguments.get("definition"), "");
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

    /**
     * The /place feature argument for one placement: the id, or the inline definition rendered as
     * SNBT. Exactly one of the two must be present.
     *
     * @param where prefix for error messages, e.g. "features[3] " (empty for the single tool)
     */
    static String featureArgument(JsonNode feature, JsonNode definition, String where) {
        boolean hasId = feature != null && !feature.isNull();
        boolean hasDef = definition != null && !definition.isNull();
        if (hasId == hasDef) {
            throw new McpException(
                    ErrorCodes.TOOL_INPUT_INVALID,
                    where + "needs exactly one of 'feature' (an id) or 'definition' (an inline feature)");
        }
        if (hasId) {
            if (!feature.isTextual() || feature.asText().isBlank()) {
                throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, where + "'feature' must be an id string");
            }
            return feature.asText();
        }
        if (!INLINE_DEFINITIONS) {
            throw new McpException(
                    ErrorCodes.TOOL_INPUT_INVALID,
                    where
                            + "'definition' needs Minecraft 26.3 or later, where /place feature accepts an"
                            + " inline feature. On this version pass a configured-feature id as 'feature'.");
        }
        if (!definition.isObject()) {
            throw new McpException(
                    ErrorCodes.TOOL_INPUT_INVALID, where + "'definition' must be a JSON object");
        }
        return definition.toString();
    }
}
