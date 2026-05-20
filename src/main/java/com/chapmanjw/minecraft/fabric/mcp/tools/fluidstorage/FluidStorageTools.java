package com.chapmanjw.minecraft.fabric.mcp.tools.fluidstorage;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.FluidStackInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Read-only access to Fabric Transfer-API {@code FluidStorage} containers. */
public final class FluidStorageTools {

    private FluidStorageTools() {}

    private static JsonNode positionSchema() {
        return Schemas.object()
                .required("x", Schemas.integer("X"))
                .required("y", Schemas.integer("Y"))
                .required("z", Schemas.integer("Z"))
                .build();
    }

    private static Vec3i readPos(JsonNode posNode) {
        return new Vec3i(posNode.get("x").asInt(), posNode.get("y").asInt(), posNode.get("z").asInt());
    }

    private static ObjectNode toJson(ObjectNode n, FluidStackInfo info) {
        n.put("empty", info.empty());
        n.put("fluid_id", info.fluidId());
        n.put("amount_droplets", info.amountDroplets());
        n.put("capacity_droplets", info.capacityDroplets());
        return n;
    }

    @McpTool(
            name = "fluid_storage_get",
            description =
                    "Reads the first fluid tank exposed by a block on the given side."
                            + " Returns empty if the block exposes no FluidStorage on that side.",
            requiredFabricModules = {"fabric-transfer-api-v1"})
    public static final class Get extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("position", positionSchema())
                        .required(
                                "direction",
                                Schemas.enumOf(
                                        "Side to probe",
                                        "up",
                                        "down",
                                        "north",
                                        "south",
                                        "east",
                                        "west",
                                        "none"))
                        .build();

        public Get() {
            super("fluid_storage_get");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            Vec3i pos = readPos(r.requireObject("position"));
            String dir = r.requireString("direction");
            return onMainThread(
                    context,
                    ignored -> {
                        var opt = context.adapter().fluidStorageGet(dim, pos, dir);
                        if (opt.isEmpty()) {
                            ObjectNode n = context.mapper().createObjectNode();
                            n.put("empty", true);
                            return ToolResult.ofToon(n);
                        }
                        return ToolResult.ofToon(
                                toJson(context.mapper().createObjectNode(), opt.get()));
                    });
        }
    }

    @McpTool(
            name = "fluid_storage_list_at",
            description =
                    "Lists every fluid stack exposed by the block at the given position."
                            + " Multi-tank blocks return more than one entry.",
            requiredFabricModules = {"fabric-transfer-api-v1"})
    public static final class ListAt extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("position", positionSchema())
                        .build();

        public ListAt() {
            super("fluid_storage_list_at");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            Vec3i pos = readPos(r.requireObject("position"));
            return onMainThread(
                    context,
                    ignored -> {
                        List<FluidStackInfo> list = context.adapter().fluidStorageListAt(dim, pos);
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (FluidStackInfo info : list) {
                            toJson(arr.addObject(), info);
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }
}
