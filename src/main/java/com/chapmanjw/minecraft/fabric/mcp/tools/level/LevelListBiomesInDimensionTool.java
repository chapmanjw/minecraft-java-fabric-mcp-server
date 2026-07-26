package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BiomeInfo;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(
        name = "level_list_biomes_in_dimension",
        description = "Lists every biome registered for a dimension.",
        requiredFabricModules = {"fabric-biome-api-v1"})
public final class LevelListBiomesInDimensionTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object().required("dimension", Schemas.string("Dimension identifier")).build();

    public LevelListBiomesInDimensionTool() {
        super("level_list_biomes_in_dimension");
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
                    var biomes = context.adapter().levelListBiomesInDimension(dim);
                    ArrayNode arr = context.mapper().createArrayNode();
                    for (BiomeInfo b : biomes) {
                        var n = arr.addObject();
                        n.put("id", b.id());
                        n.put("temperature", b.temperature());
                        n.put("downfall", b.downfall());
                        n.put("hasPrecipitation", b.hasPrecipitation());
                        // Position-dependent, so absent when the descriptor had no block to resolve against.
                        if (b.precipitation() != null) {
                            n.put("precipitation", b.precipitation());
                        }
                        if (b.grassColor() != null) {
                            n.put("grassColor", BiomeInfo.hex(b.grassColor()));
                        }
                        n.put("foliageColor", BiomeInfo.hex(b.foliageColor()));
                        n.put("dryFoliageColor", BiomeInfo.hex(b.dryFoliageColor()));
                        n.put("waterColor", BiomeInfo.hex(b.waterColor()));
                        n.put("grassColorModifier", b.grassColorModifier());
                    }
                    return ToolResult.ofToon(arr);
                });
    }
}
