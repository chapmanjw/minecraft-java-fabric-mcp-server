package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(
        name = "level_poi_query",
        description =
                "Lists the points of interest the server's village AI tracks near a position."
                        + " Beds, workstations and bells are all POI records, so this reports what"
                        + " the GAME believes about a build rather than what it looks like: a bed"
                        + " villagers cannot claim simply is not a home POI, however convincing the"
                        + " room around it. Each result gives the POI type, its block position,"
                        + " whether it is occupied (for a bed, that a villager claimed it) and how"
                        + " many claims remain. Use it to verify a village actually functions, and"
                        + " to find which beds or workstations went unclaimed.",
        readOnly = true)
public final class LevelPoiQueryTool extends BaseTool {

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
                    .required(
                            "radius",
                            Schemas.integerBetween("Search radius in blocks", 1, 128))
                    .build();

    public LevelPoiQueryTool() {
        super("level_poi_query");
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
        int x = pos.get("x").asInt();
        int y = pos.get("y").asInt();
        int z = pos.get("z").asInt();
        int radius = r.requireInt("radius");
        // Optional filter; absent means every POI type.
        String type = arguments.hasNonNull("type") ? arguments.get("type").asText() : null;

        return onMainThread(
                context,
                ignored -> {
                    var found = context.adapter().poiQuery(dim, x, y, z, radius, type);
                    ObjectNode out = context.mapper().createObjectNode();
                    out.put("count", found.size());
                    ArrayNode arr = out.putArray("poi");
                    for (var p : found) {
                        ObjectNode n = arr.addObject();
                        n.put("type", p.type());
                        ObjectNode at = n.putObject("pos");
                        at.put("x", p.pos().x());
                        at.put("y", p.pos().y());
                        at.put("z", p.pos().z());
                        n.put("occupied", p.occupied());
                        n.put("free_tickets", p.freeTickets());
                    }
                    return ToolResult.ofToon(out);
                });
    }
}
