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
 * Paints the biome of a region via {@code /fillbiome} — biome is otherwise
 * read-only over MCP. Biome controls foliage/water tint, mob spawns, ambient
 * sound, and climate, so painting it makes a built landform read as the biome it
 * portrays (a snowy peak, a swamp basin) rather than borrowing the surrounding
 * biome's grass colour.
 *
 * <p>The region is bounded by {@code /fillbiome}'s own volume limit
 * (chunk-section aligned, large but finite); for very large areas, tile the calls.
 */
@McpTool(
        name = "level_fill_biome",
        description =
                "Paints the biome of a region via /fillbiome (biome is read-only otherwise). Sets"
                        + " foliage/water tint, mob spawns, and climate so a built landform reads as its"
                        + " biome. Args: dimension, from/to corners {x,y,z}, biome id, optional"
                        + " replace_filter (only overwrite that biome).")
public final class LevelFillBiomeTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object()
                    .required("dimension", Schemas.string("Dimension identifier"))
                    .required("from", Schemas.position3d("Inclusive minimum corner"))
                    .required("to", Schemas.position3d("Inclusive maximum corner"))
                    .required("biome", Schemas.string("Biome id, e.g. minecraft:snowy_taiga"))
                    .optional(
                            "replace_filter",
                            Schemas.string(
                                    "Only overwrite this biome id (vanilla 'replace' filter); omit to"
                                            + " overwrite all biomes in the region"))
                    .build();

    public LevelFillBiomeTool() {
        super("level_fill_biome");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        var r = reader(arguments);
        String dim = r.requireString("dimension");
        var from = r.requireObject("from");
        var to = r.requireObject("to");
        String biome = r.requireString("biome");
        String replaceFilter = r.optString("replace_filter", null);
        String base =
                String.format(
                        Locale.ROOT,
                        "execute in %s run fillbiome %d %d %d %d %d %d %s",
                        dim,
                        from.get("x").asInt(),
                        from.get("y").asInt(),
                        from.get("z").asInt(),
                        to.get("x").asInt(),
                        to.get("y").asInt(),
                        to.get("z").asInt(),
                        biome);
        String cmd = replaceFilter == null ? base : base + " replace " + replaceFilter;
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
