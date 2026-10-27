package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.MobSpawnEntry;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/**
 * Reports what can spawn naturally at a position: the weighted lists the game's own spawner
 * would draw from, grouped by mob category. Structure overrides are included, so a position in a
 * witch hut reports witches rather than the swamp's usual monsters.
 */
@McpTool(
        name = "level_get_mob_spawns",
        description =
                "Lists what can spawn naturally at a position, grouped by mob category (monster,"
                        + " creature, ambient, water_creature, ...): each entry's entity type, weight"
                        + " and group size. Uses the game's own spawn lookup, so structure overrides"
                        + " (witch huts, ocean monuments, fortresses) are included, not only the biome."
                        + " Use it to check a painted biome will spawn the intended animals or to plan"
                        + " mob-proofing. Optional category narrows the result.")
public final class LevelGetMobSpawnsTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object()
                    .required("dimension", Schemas.string("Dimension identifier, e.g. minecraft:overworld"))
                    .required(
                            "position",
                            Schemas.object()
                                    .required("x", Schemas.integer("X"))
                                    .required("y", Schemas.integer("Y"))
                                    .required("z", Schemas.integer("Z"))
                                    .build())
                    .optional(
                            "category",
                            Schemas.string("Only this mob category, e.g. monster or creature"))
                    .build();

    public LevelGetMobSpawnsTool() {
        super("level_get_mob_spawns");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        var r = reader(arguments);
        String dim = r.requireString("dimension");
        var p = r.requireObject("position");
        Vec3i pos = new Vec3i(p.get("x").asInt(), p.get("y").asInt(), p.get("z").asInt());
        String category = r.optString("category", null);
        return onMainThread(
                context,
                ignored -> {
                    List<MobSpawnEntry> entries = context.adapter().levelGetMobSpawns(dim, pos, category);
                    Map<String, ArrayNode> byCategory = new LinkedHashMap<>();
                    ObjectNode payload = context.mapper().createObjectNode();
                    ObjectNode cats = payload.putObject("categories");
                    for (MobSpawnEntry e : entries) {
                        ArrayNode arr = byCategory.computeIfAbsent(e.category(), cats::putArray);
                        ObjectNode n = arr.addObject();
                        n.put("type", e.entityType());
                        n.put("weight", e.weight());
                        n.put("min_count", e.minCount());
                        n.put("max_count", e.maxCount());
                    }
                    payload.put("total_entries", entries.size());
                    return ToolResult.ofToon(payload);
                });
    }
}
