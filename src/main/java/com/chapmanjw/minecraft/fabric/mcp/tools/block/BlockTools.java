package com.chapmanjw.minecraft.fabric.mcp.tools.block;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.MinecraftAdapter;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BlockSpec;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BlockStateInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BoundingBox;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.Jsons;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Block / BlockState operation tools. */
public final class BlockTools {

    private BlockTools() {}

    private static Vec3i readVec3i(JsonNode node) {
        return new Vec3i(node.get("x").asInt(), node.get("y").asInt(), node.get("z").asInt());
    }

    private static BoundingBox readBox(JsonNode node) {
        return BoundingBox.of(readVec3i(node.get("from")), readVec3i(node.get("to")));
    }

    private static BlockSpec readBlockSpec(JsonNode node) {
        if (node == null) {
            throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "block spec must be provided");
        }
        String id = node.get("id").asText();
        Map<String, String> props = new LinkedHashMap<>();
        JsonNode p = node.get("properties");
        if (p != null && p.isObject()) {
            p.fields().forEachRemaining(e -> props.put(e.getKey(), e.getValue().asText()));
        }
        String nbt = node.has("nbt") ? node.get("nbt").asText() : null;
        return new BlockSpec(id, props, nbt);
    }

    private static JsonNode blockSpecSchema() {
        return Schemas.object()
                .required("id", Schemas.string("Block identifier (e.g. minecraft:oak_log)"))
                .optional(
                        "properties",
                        Schemas.object().description("Map of blockstate properties").allowAdditional().build())
                .optional("nbt", Schemas.string("SNBT block-entity tag (optional)"))
                .build();
    }

    // -------------------------------------------------------------------
    // block_get_state
    // -------------------------------------------------------------------
    @McpTool(
            name = "block_get_state",
            description =
                    "Returns the block at a position: identifier, blockstate properties, light level, hardness,"
                            + " and block entity NBT (if any).")
    public static final class GetState extends BaseTool {
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
                        .build();

        public GetState() {
            super("block_get_state");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            Vec3i pos = readVec3i(r.requireObject("position"));
            return onMainThread(
                    context,
                    ignored -> {
                        BlockStateInfo info =
                                context.adapter()
                                        .blockGetState(dim, pos)
                                        .orElseThrow(
                                                () ->
                                                        new McpException(
                                                                ErrorCodes.TOOL_HANDLER_ERROR,
                                                                "Block position not loaded"));
                        ObjectNode payload = Jsons.blockState(context.mapper(), info);
                        payload.set("position", Jsons.vec3i(context.mapper(), pos));
                        return ToolResult.ofToon(payload);
                    });
        }
    }

    // -------------------------------------------------------------------
    // block_set_state
    // -------------------------------------------------------------------
    @McpTool(
            name = "block_set_state",
            description = "Set the block at a position. Accepts a block id with optional block-state properties (e.g. minecraft:oak_log[axis=y]) and optional NBT. Overwrites whatever was there.")
    public static final class SetState extends BaseTool {
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
                        .required("block", blockSpecSchema())
                        .optional(
                                "update_flags",
                                Schemas.integer("Vanilla update flags (default 3: notify + sync)"))
                        .build();

        public SetState() {
            super("block_set_state");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            Vec3i pos = readVec3i(r.requireObject("position"));
            BlockSpec spec = readBlockSpec(r.requireObject("block"));
            int flags = r.optInt("update_flags", 3);
            return onMainThread(
                    context,
                    ignored -> {
                        boolean ok = context.adapter().blockSetState(dim, pos, spec, flags);
                        return ToolResult.ofText(ok ? "placed " + spec.id() : "no change");
                    });
        }
    }

    // -------------------------------------------------------------------
    // block_fill_region
    // -------------------------------------------------------------------
    @McpTool(
            name = "block_fill_region",
            description =
                    "Bulk fill an inclusive bounding box. Volumes up to 32768 blocks are synchronous;"
                            + " larger fills use vanilla /fill chunking.")
    public static final class FillRegion extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("box", Schemas.box3d("Inclusive bounding box"))
                        .required("block", blockSpecSchema())
                        .optional(
                                "mode",
                                Schemas.enumOf(
                                        "Fill mode (default replace)",
                                        "replace",
                                        "destroy",
                                        "hollow",
                                        "outline",
                                        "keep"))
                        .build();

        public FillRegion() {
            super("block_fill_region");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            BoundingBox box = readBox(r.requireObject("box"));
            BlockSpec spec = readBlockSpec(r.requireObject("block"));
            String modeStr = r.optString("mode", "replace");
            MinecraftAdapter.FillMode mode =
                    MinecraftAdapter.FillMode.valueOf(modeStr.toUpperCase(java.util.Locale.ROOT));
            return onMainThread(
                    context,
                    ignored -> {
                        long changed = context.adapter().blockFillRegion(dim, box, spec, mode);
                        return ToolResult.ofText("filled " + changed + " block(s)");
                    });
        }
    }

    // -------------------------------------------------------------------
    // block_clone_region
    // -------------------------------------------------------------------
    @McpTool(name = "block_clone_region", description = "Copies blocks from one bounding box to another.")
    public static final class CloneRegion extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("source_dimension", Schemas.string("Source dimension"))
                        .required("source_box", Schemas.box3d("Source bounding box"))
                        .required("dest_dimension", Schemas.string("Destination dimension"))
                        .required("destination", Schemas.position3d("Destination origin (min corner)"))
                        .optional(
                                "mode", Schemas.enumOf("Clone mode", "normal", "masked", "move"))
                        .build();

        public CloneRegion() {
            super("block_clone_region");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String srcDim = r.requireString("source_dimension");
            BoundingBox src = readBox(r.requireObject("source_box"));
            String destDim = r.requireString("dest_dimension");
            Vec3i dest = readVec3i(r.requireObject("destination"));
            String modeStr = r.optString("mode", "normal");
            MinecraftAdapter.CloneMode mode =
                    MinecraftAdapter.CloneMode.valueOf(modeStr.toUpperCase(java.util.Locale.ROOT));
            return onMainThread(
                    context,
                    ignored -> {
                        long count = context.adapter().blockCloneRegion(srcDim, src, destDim, dest, mode);
                        return ToolResult.ofText("cloned " + count + " block(s)");
                    });
        }
    }

    // -------------------------------------------------------------------
    // block_replace_in_region
    // -------------------------------------------------------------------
    @McpTool(
            name = "block_replace_in_region",
            description = "Replaces every matching block in a region with a new block.")
    public static final class ReplaceInRegion extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("box", Schemas.box3d("Bounding box"))
                        .required("target", Schemas.string("Block identifier to replace"))
                        .required("replacement", blockSpecSchema())
                        .build();

        public ReplaceInRegion() {
            super("block_replace_in_region");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            BoundingBox box = readBox(r.requireObject("box"));
            String target = r.requireString("target");
            BlockSpec spec = readBlockSpec(r.requireObject("replacement"));
            return onMainThread(
                    context,
                    ignored -> {
                        long count = context.adapter().blockReplaceInRegion(dim, box, target, spec);
                        return ToolResult.ofText("replaced " + count + " block(s)");
                    });
        }
    }

    // -------------------------------------------------------------------
    // block_get_top_y
    // -------------------------------------------------------------------
    @McpTool(
            name = "block_get_top_y",
            description = "Returns the highest non-air Y at a given (x, z) column.")
    public static final class GetTopY extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("x", Schemas.integer("Block X"))
                        .required("z", Schemas.integer("Block Z"))
                        .build();

        public GetTopY() {
            super("block_get_top_y");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            int x = r.requireInt("x");
            int z = r.requireInt("z");
            return onMainThread(
                    context,
                    ignored -> {
                        int y = context.adapter().blockGetTopY(dim, x, z);
                        return ToolResult.ofText(String.valueOf(y));
                    });
        }
    }

    // -------------------------------------------------------------------
    // block_scan_region
    // -------------------------------------------------------------------
    @McpTool(
            name = "block_scan_region",
            description =
                    "Scans a bounding box and returns all matching blocks. Bounding box volume capped at"
                            + " 65,536; results capped at 'limit' (default 1024).")
    public static final class ScanRegion extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("box", Schemas.box3d("Bounding box (volume <= 65536)"))
                        .optional("match_block_id", Schemas.string("Block id to match (omit for all blocks)"))
                        .optional("limit", Schemas.integerBetween("Max matches to return", 1, 65536))
                        .build();

        public ScanRegion() {
            super("block_scan_region");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            BoundingBox box = readBox(r.requireObject("box"));
            String match = r.optString("match_block_id", null);
            int limit = r.optInt("limit", 1024);
            return onMainThread(
                    context,
                    ignored -> {
                        var matches = context.adapter().blockScanRegion(dim, box, match, limit);
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (MinecraftAdapter.BlockMatch m : matches) {
                            ObjectNode n = arr.addObject();
                            n.set("position", Jsons.vec3i(context.mapper(), m.position()));
                            n.set("state", Jsons.blockState(context.mapper(), m.state()));
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }
}
