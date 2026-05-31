package com.chapmanjw.minecraft.fabric.mcp.tools.block;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.MinecraftAdapter;
import com.chapmanjw.minecraft.fabric.mcp.runtime.ErosionJob;
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
        if (node == null || !node.isObject()) {
            throw new McpException(
                    ErrorCodes.TOOL_INPUT_INVALID,
                    "position must be an object with int x, y, z");
        }
        JsonNode x = node.get("x");
        JsonNode y = node.get("y");
        JsonNode z = node.get("z");
        if (x == null || y == null || z == null) {
            throw new McpException(
                    ErrorCodes.TOOL_INPUT_INVALID,
                    "position requires int x, y, z");
        }
        return new Vec3i(x.asInt(), y.asInt(), z.asInt());
    }

    private static BoundingBox readBox(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new McpException(
                    ErrorCodes.TOOL_INPUT_INVALID,
                    "box must be an object with from/to corners");
        }
        JsonNode from = node.get("from");
        JsonNode to = node.get("to");
        if (from == null || to == null) {
            throw new McpException(
                    ErrorCodes.TOOL_INPUT_INVALID,
                    "box requires from and to corners, each {x,y,z}");
        }
        return BoundingBox.of(readVec3i(from), readVec3i(to));
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

    /** Read a position from either a {@code [x,y,z]} array or a {@code {x,y,z}} object. */
    private static Vec3i readVec3iFlexible(JsonNode node) {
        if (node != null && node.isArray() && node.size() == 3) {
            return new Vec3i(node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
        }
        return readVec3i(node);
    }

    /** Parse a block-state string ("minecraft:oak_log[axis=y]") into a BlockSpec (no NBT). */
    private static BlockSpec parseBlockString(String s) {
        if (s == null || s.isBlank()) {
            throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "fill entry: 'block' must be a non-empty id");
        }
        s = s.trim();
        int br = s.indexOf('[');
        if (br < 0) {
            return new BlockSpec(s, new LinkedHashMap<>(), null);
        }
        String id = s.substring(0, br);
        int end = s.endsWith("]") ? s.length() - 1 : s.length();
        Map<String, String> props = new LinkedHashMap<>();
        for (String kv : s.substring(br + 1, end).split(",")) {
            int eq = kv.indexOf('=');
            if (eq > 0) {
                props.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
        }
        return new BlockSpec(id, props, null);
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
                    "Bulk fill an inclusive bounding box. Any volume is accepted: fills larger than the"
                            + " vanilla 32768-block /fill cap are auto-tiled server-side (so they never"
                            + " silently no-op), and hollow/outline are decomposed into faces. Returns the"
                            + " total blocks changed. For many separate fills, prefer block_fill_batch.")
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
            description =
                    "Returns the highest Y at an (x, z) column for a heightmap. heightmap:"
                            + " WORLD_SURFACE (default) | OCEAN_FLOOR (top non-fluid solid, for seabed) |"
                            + " MOTION_BLOCKING | MOTION_BLOCKING_NO_LEAVES | WORLD_SURFACE_WG | OCEAN_FLOOR_WG.")
    public static final class GetTopY extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("x", Schemas.integer("Block X"))
                        .required("z", Schemas.integer("Block Z"))
                        .optional(
                                "heightmap",
                                Schemas.enumOf(
                                        "Heightmap type (default WORLD_SURFACE)",
                                        "WORLD_SURFACE",
                                        "OCEAN_FLOOR",
                                        "MOTION_BLOCKING",
                                        "MOTION_BLOCKING_NO_LEAVES",
                                        "WORLD_SURFACE_WG",
                                        "OCEAN_FLOOR_WG"))
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
            String heightmap = r.optString("heightmap", "WORLD_SURFACE");
            return onMainThread(
                    context,
                    ignored -> {
                        int y = context.adapter().blockGetTopY(dim, x, z, heightmap);
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

    // -------------------------------------------------------------------
    // block_fill_batch
    // -------------------------------------------------------------------
    @McpTool(
            name = "block_fill_batch",
            description =
                    "Apply many fills in ONE call — the efficient way to place a generated/voxelized"
                            + " build. Each entry is {from:[x,y,z], to:[x,y,z], block:\"id[state]\", mode?}."
                            + " Each fill is auto-tiled to the vanilla cap. Returns total blocks changed and"
                            + " fills applied. Bounded to 8192 entries per call — page larger batches.")
    public static final class FillBatch extends BaseTool {
        private static final int MAX_ENTRIES = 8192;
        private static final JsonNode FILL_ITEM =
                Schemas.object()
                        .required("from", Schemas.arrayOf("Min corner [x,y,z]", Schemas.integer("coord")))
                        .required("to", Schemas.arrayOf("Max corner [x,y,z]", Schemas.integer("coord")))
                        .required(
                                "block",
                                Schemas.string("Block id with optional [state], e.g. minecraft:cyan_concrete"))
                        .optional(
                                "mode",
                                Schemas.enumOf(
                                        "Fill mode for this entry",
                                        "replace",
                                        "destroy",
                                        "hollow",
                                        "outline",
                                        "keep"))
                        .build();
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("fills", Schemas.arrayOf("Fills to apply, in order", FILL_ITEM))
                        .optional(
                                "default_mode",
                                Schemas.enumOf(
                                        "Mode for entries without their own (default replace)",
                                        "replace",
                                        "destroy",
                                        "hollow",
                                        "outline",
                                        "keep"))
                        .build();

        public FillBatch() {
            super("block_fill_batch");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            JsonNode fills = r.requireArray("fills");
            if (fills.size() > MAX_ENTRIES) {
                throw new McpException(
                        ErrorCodes.TOOL_INPUT_INVALID,
                        "block_fill_batch: "
                                + fills.size()
                                + " fills exceeds the "
                                + MAX_ENTRIES
                                + "-entry cap; split the batch");
            }
            String defaultMode = r.optString("default_mode", "replace");
            return onMainThread(
                    context,
                    ignored -> {
                        long totalChanged = 0;
                        int applied = 0;
                        for (JsonNode entry : fills) {
                            BoundingBox box =
                                    BoundingBox.of(
                                            readVec3iFlexible(entry.get("from")),
                                            readVec3iFlexible(entry.get("to")));
                            JsonNode blockNode = entry.get("block");
                            if (blockNode == null || !blockNode.isTextual()) {
                                throw new McpException(
                                        ErrorCodes.TOOL_INPUT_INVALID,
                                        "block_fill_batch: each fill needs a 'block' string");
                            }
                            BlockSpec spec = parseBlockString(blockNode.asText());
                            String modeStr =
                                    entry.has("mode") && entry.get("mode").isTextual()
                                            ? entry.get("mode").asText()
                                            : defaultMode;
                            MinecraftAdapter.FillMode mode =
                                    MinecraftAdapter.FillMode.valueOf(
                                            modeStr.toUpperCase(java.util.Locale.ROOT));
                            totalChanged += context.adapter().blockFillRegion(dim, box, spec, mode);
                            applied++;
                        }
                        ObjectNode payload = context.mapper().createObjectNode();
                        payload.put("fills_applied", applied);
                        payload.put("blocks_changed", totalChanged);
                        return ToolResult.ofToon(payload);
                    });
        }
    }

    // -------------------------------------------------------------------
    // block_fill_columns
    // -------------------------------------------------------------------
    @McpTool(
            name = "block_fill_columns",
            description =
                    "Materialise a per-column heightmap into terrain in ONE call — the efficient path"
                            + " for generated landforms. Send a compact height grid + a small palette"
                            + " instead of thousands of box fills (no 8192-entry cap). Each column fills"
                            + " stone -> subsurface -> surface, and floods to sea_level where the surface is"
                            + " below it. Arrays are row-major, length width*length, index xi*length+zi."
                            + " Columns capped at 65,536 per call — tile larger terrain.")
    public static final class FillColumns extends BaseTool {
        private static final long MAX_COLUMNS = 65_536L;
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required(
                                "origin",
                                Schemas.object()
                                        .description("World (x, z) of column index (0,0)")
                                        .required("x", Schemas.integer("Origin X"))
                                        .required("z", Schemas.integer("Origin Z"))
                                        .build())
                        .required("width", Schemas.integerBetween("Columns along X", 1, 65536))
                        .required("length", Schemas.integerBetween("Columns along Z", 1, 65536))
                        .required("floor_y", Schemas.integer("Bottom Y; stone fills from here up"))
                        .required(
                                "palette",
                                Schemas.arrayOf("Block ids; surface/subsurface index into this", Schemas.string("Block id")))
                        .required("stone_index", Schemas.integer("Palette index for deep stone"))
                        .required("height", Schemas.arrayOf("Surface Y per column", Schemas.integer("Y")))
                        .required("surface", Schemas.arrayOf("Palette index of the surface block per column", Schemas.integer("idx")))
                        .required("subsurface", Schemas.arrayOf("Palette index of the subsurface block per column", Schemas.integer("idx")))
                        .optional("subsurface_depth", Schemas.integerBetween("Subsurface thickness (default 3)", 0, 64))
                        .optional("sea_level", Schemas.integer("Flood columns below this Y with water"))
                        .optional("water_index", Schemas.integer("Palette index for water (required if sea_level set)"))
                        .build();

        public FillColumns() {
            super("block_fill_columns");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            var origin = r.requireObject("origin");
            int originX = origin.get("x").asInt();
            int originZ = origin.get("z").asInt();
            int width = r.requireInt("width");
            int length = r.requireInt("length");
            long columns = (long) width * length;
            if (columns > MAX_COLUMNS) {
                throw new McpException(
                        ErrorCodes.TOOL_INPUT_INVALID,
                        "block_fill_columns: " + columns + " columns exceed the " + MAX_COLUMNS
                                + "-column cap; tile the heightmap");
            }
            int expected = (int) columns;
            int floorY = r.requireInt("floor_y");
            java.util.List<String> palette = readStringArray(r.requireArray("palette"), "palette");
            int stoneIndex = r.requireInt("stone_index");
            int[] height = readIntArray(r.requireArray("height"), "height", expected);
            int[] surface = readIntArray(r.requireArray("surface"), "surface", expected);
            int[] subsurface = readIntArray(r.requireArray("subsurface"), "subsurface", expected);
            int subDepth = r.optInt("subsurface_depth", 3);
            int seaLevel = r.optInt("sea_level", Integer.MIN_VALUE);
            int waterIndex = r.optInt("water_index", -1);

            MinecraftAdapter.ColumnFill spec =
                    new MinecraftAdapter.ColumnFill(
                            originX, originZ, width, length, floorY, seaLevel, palette,
                            subDepth, stoneIndex, waterIndex, height, surface, subsurface);
            return onMainThread(
                    context,
                    ignored -> {
                        long set = context.adapter().blockFillColumns(dim, spec);
                        ObjectNode payload = context.mapper().createObjectNode();
                        payload.put("columns", expected);
                        payload.put("blocks_set", set);
                        return ToolResult.ofToon(payload);
                    });
        }
    }

    /**
     * Strata-banded {@code block_fill_columns}: same per-column heightmap fill, but
     * the deep mass below the subsurface is banded into geological strata instead of
     * one stone block — the signature of canyons, mesas, and badlands. Bands run
     * top→bottom; below the deepest band it is {@code base_stone}. Optional smooth
     * low-frequency jitter wobbles the band boundaries so they are not dead-flat.
     */
    @McpTool(
            name = "block_fill_columns_strata",
            description =
                    "Like block_fill_columns but bands the deep fill into geological strata (canyon /"
                            + " mesa / badlands signature). Adds: strata[] of {block, thickness}"
                            + " top->bottom, base_stone below the deepest band, optional"
                            + " jitter_amplitude/jitter_freq for smooth non-flat band boundaries."
                            + " Same 65,536-column cap; row-major arrays index xi*length+zi.")
    public static final class FillColumnsStrata extends BaseTool {
        private static final long MAX_COLUMNS = 65_536L;
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required(
                                "origin",
                                Schemas.object()
                                        .description("World (x, z) of column index (0,0)")
                                        .required("x", Schemas.integer("Origin X"))
                                        .required("z", Schemas.integer("Origin Z"))
                                        .build())
                        .required("width", Schemas.integerBetween("Columns along X", 1, 65536))
                        .required("length", Schemas.integerBetween("Columns along Z", 1, 65536))
                        .required("floor_y", Schemas.integer("Bottom Y; strata fill from here up"))
                        .required(
                                "palette",
                                Schemas.arrayOf(
                                        "Block ids; surface/subsurface index into this",
                                        Schemas.string("Block id")))
                        .required("height", Schemas.arrayOf("Surface Y per column", Schemas.integer("Y")))
                        .required(
                                "surface",
                                Schemas.arrayOf(
                                        "Palette index of the surface block per column",
                                        Schemas.integer("idx")))
                        .required(
                                "subsurface",
                                Schemas.arrayOf(
                                        "Palette index of the subsurface block per column",
                                        Schemas.integer("idx")))
                        .required(
                                "strata",
                                Schemas.arrayOf(
                                        "Strata bands top->bottom, each {block, thickness}",
                                        Schemas.object()
                                                .required("block", Schemas.string("Band block id"))
                                                .required(
                                                        "thickness",
                                                        Schemas.integerBetween(
                                                                "Band thickness in blocks", 1, 256))
                                                .build()))
                        .required(
                                "base_stone",
                                Schemas.string("Block below the deepest band, e.g. minecraft:stone"))
                        .optional(
                                "subsurface_depth",
                                Schemas.integerBetween("Subsurface thickness (default 3)", 0, 64))
                        .optional("sea_level", Schemas.integer("Flood columns below this Y with water"))
                        .optional(
                                "water_index",
                                Schemas.integer("Palette index for water (required if sea_level set)"))
                        .optional(
                                "jitter_amplitude",
                                Schemas.integerBetween(
                                        "Max band-boundary wobble in blocks (default 0)", 0, 32))
                        .optional(
                                "jitter_freq",
                                Schemas.number("Spatial frequency of the band wobble (default 0.05)"))
                        .build();

        public FillColumnsStrata() {
            super("block_fill_columns_strata");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            var origin = r.requireObject("origin");
            int originX = origin.get("x").asInt();
            int originZ = origin.get("z").asInt();
            int width = r.requireInt("width");
            int length = r.requireInt("length");
            long columns = (long) width * length;
            if (columns > MAX_COLUMNS) {
                throw new McpException(
                        ErrorCodes.TOOL_INPUT_INVALID,
                        "block_fill_columns_strata: " + columns + " columns exceed the " + MAX_COLUMNS
                                + "-column cap; tile the heightmap");
            }
            int expected = (int) columns;
            int floorY = r.requireInt("floor_y");
            java.util.List<String> palette = readStringArray(r.requireArray("palette"), "palette");
            int[] height = readIntArray(r.requireArray("height"), "height", expected);
            int[] surface = readIntArray(r.requireArray("surface"), "surface", expected);
            int[] subsurface = readIntArray(r.requireArray("subsurface"), "subsurface", expected);
            int subDepth = r.optInt("subsurface_depth", 3);
            int seaLevel = r.optInt("sea_level", Integer.MIN_VALUE);
            int waterIndex = r.optInt("water_index", -1);
            int jitterAmp = r.optInt("jitter_amplitude", 0);
            double jitterFreq = r.optDouble("jitter_freq", 0.05);

            JsonNode strataArr = r.requireArray("strata");
            java.util.List<String> strataBlocks = new java.util.ArrayList<>(strataArr.size());
            int[] strataThk = new int[strataArr.size()];
            for (int i = 0; i < strataArr.size(); i++) {
                JsonNode band = strataArr.get(i);
                JsonNode b = band.get("block");
                JsonNode t = band.get("thickness");
                if (b == null || !b.isTextual() || t == null || !t.isIntegralNumber()) {
                    throw new McpException(
                            ErrorCodes.TOOL_INPUT_INVALID,
                            "block_fill_columns_strata: strata["
                                    + i
                                    + "] must be {block:string, thickness:int}");
                }
                strataBlocks.add(b.asText());
                strataThk[i] = t.intValue();
            }
            String baseStone = r.requireString("base_stone");

            MinecraftAdapter.ColumnStrataFill spec =
                    new MinecraftAdapter.ColumnStrataFill(
                            originX, originZ, width, length, floorY, seaLevel, palette,
                            subDepth, waterIndex, height, surface, subsurface,
                            strataBlocks, strataThk, baseStone, jitterAmp, jitterFreq);
            return onMainThread(
                    context,
                    ignored -> {
                        long set = context.adapter().blockFillColumnsStrata(dim, spec);
                        ObjectNode payload = context.mapper().createObjectNode();
                        payload.put("columns", expected);
                        payload.put("blocks_set", set);
                        return ToolResult.ofToon(payload);
                    });
        }
    }

    @McpTool(
            name = "block_erode_region",
            description =
                    "Thermal-erodes an existing terrain region: reads the live surface, runs talus"
                            + " collapse, then re-materialises surface+subsurface to the new profile."
                            + " dry_run reports max/mean height delta with no writes. protect_box (with"
                            + " a smoothstep apron) shields built structures so terrain naturalises into"
                            + " them. Synchronous; same 65,536-column cap.")
    public static final class ErodeRegion extends BaseTool {
        private static final long MAX_COLUMNS = 65_536L;
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required(
                                "origin",
                                Schemas.object()
                                        .description("World (x, z) of column index (0,0)")
                                        .required("x", Schemas.integer("Origin X"))
                                        .required("z", Schemas.integer("Origin Z"))
                                        .build())
                        .required("width", Schemas.integerBetween("Columns along X", 1, 65536))
                        .required("length", Schemas.integerBetween("Columns along Z", 1, 65536))
                        .required("floor_y", Schemas.integer("Lowest Y erosion may carve to"))
                        .optional(
                                "surface",
                                Schemas.string("Surface cap block (default minecraft:grass_block)"))
                        .optional(
                                "subsurface",
                                Schemas.string("Subsurface block (default minecraft:dirt)"))
                        .optional(
                                "subsurface_depth",
                                Schemas.integerBetween("Subsurface thickness (default 3)", 0, 64))
                        .optional(
                                "iterations",
                                Schemas.integerBetween("Thermal sweeps (default 8)", 1, 200))
                        .optional(
                                "talus",
                                Schemas.number("Max stable neighbour height diff in blocks (default 1.0)"))
                        .optional(
                                "strength",
                                Schemas.number("Fraction of excess moved per sweep, 0..1 (default 0.5)"))
                        .optional(
                                "protect_box",
                                Schemas.object()
                                        .description("Region left uneroded (e.g. a built structure)")
                                        .required("x0", Schemas.integer("Min X"))
                                        .required("z0", Schemas.integer("Min Z"))
                                        .required("x1", Schemas.integer("Max X"))
                                        .required("z1", Schemas.integer("Max Z"))
                                        .build())
                        .optional(
                                "apron",
                                Schemas.integerBetween("Taper width around protect_box (default 0)", 0, 128))
                        .optional(
                                "dry_run",
                                Schemas.bool("Report stats without writing blocks (default false)"))
                        .build();

        public ErodeRegion() {
            super("block_erode_region");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            var origin = r.requireObject("origin");
            int originX = origin.get("x").asInt();
            int originZ = origin.get("z").asInt();
            int width = r.requireInt("width");
            int length = r.requireInt("length");
            long columns = (long) width * length;
            if (columns > MAX_COLUMNS) {
                throw new McpException(
                        ErrorCodes.TOOL_INPUT_INVALID,
                        "block_erode_region: " + columns + " columns exceed the " + MAX_COLUMNS
                                + "-column cap; tile the region");
            }
            int floorY = r.requireInt("floor_y");
            String surface = r.optString("surface", "minecraft:grass_block");
            String subsurface = r.optString("subsurface", "minecraft:dirt");
            int subDepth = r.optInt("subsurface_depth", 3);
            int iterations = r.optInt("iterations", 8);
            double talus = r.optDouble("talus", 1.0);
            double strength = r.optDouble("strength", 0.5);
            int apron = r.optInt("apron", 0);
            boolean dryRun = r.optBoolean("dry_run", false);

            int px0 = Integer.MIN_VALUE;
            int pz0 = Integer.MIN_VALUE;
            int px1 = Integer.MIN_VALUE;
            int pz1 = Integer.MIN_VALUE;
            JsonNode protect = arguments.get("protect_box");
            if (protect != null && protect.isObject()) {
                px0 = protect.get("x0").asInt();
                pz0 = protect.get("z0").asInt();
                px1 = protect.get("x1").asInt();
                pz1 = protect.get("z1").asInt();
            }

            MinecraftAdapter.ErodeSpec spec =
                    new MinecraftAdapter.ErodeSpec(
                            originX, originZ, width, length, floorY,
                            iterations, talus, strength,
                            surface, subsurface, subDepth,
                            px0, pz0, px1, pz1, apron, dryRun);
            return onMainThread(
                    context,
                    ignored -> {
                        MinecraftAdapter.ErodeResult res =
                                context.adapter().terrainErodeRegion(dim, spec);
                        ObjectNode payload = context.mapper().createObjectNode();
                        payload.put("columns", res.columns());
                        payload.put("blocks_changed", res.blocksChanged());
                        payload.put("max_delta", res.maxDelta());
                        payload.put("mean_abs_delta", res.meanAbsDelta());
                        payload.put("moved", res.moved());
                        payload.put("iterations", res.iterations());
                        payload.put("dry_run", dryRun);
                        // Dry run returns the flat row-major (xi*length+zi) eroded height
                        // grid so the offline client can render-verify before applying.
                        if (res.heights() != null) {
                            ArrayNode hs = payload.putArray("heights");
                            for (int hv : res.heights()) {
                                hs.add(hv);
                            }
                        }
                        return ToolResult.ofToon(payload);
                    });
        }
    }

    @McpTool(
            name = "block_erode_hydraulic_start",
            description =
                    "Start an async hydraulic (droplet) erosion job over a region: surveys the live"
                            + " surface, simulates rain droplets carving channels/valleys on a worker"
                            + " thread, then (unless dry_run) writes the result back chunked across"
                            + " server ticks. Returns a job_id; poll block_erode_hydraulic_status and"
                            + " read block_erode_hydraulic_result. protect_box + apron shield built"
                            + " structures. Default region cap 256x256, hard cap 512x512.")
    public static final class HydraulicErodeStart extends BaseTool {
        private static final long DEFAULT_CAP = 65_536L;
        private static final long HARD_CAP = 262_144L;
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required(
                                "origin",
                                Schemas.object()
                                        .description("World (x, z) of column index (0,0)")
                                        .required("x", Schemas.integer("Origin X"))
                                        .required("z", Schemas.integer("Origin Z"))
                                        .build())
                        .required("width", Schemas.integerBetween("Columns along X", 1, 512))
                        .required("length", Schemas.integerBetween("Columns along Z", 1, 512))
                        .required("floor_y", Schemas.integer("Lowest Y erosion may carve to"))
                        .optional("surface", Schemas.string("Surface cap block (default minecraft:grass_block)"))
                        .optional("subsurface", Schemas.string("Subsurface block (default minecraft:dirt)"))
                        .optional(
                                "subsurface_depth",
                                Schemas.integerBetween("Subsurface thickness (default 3)", 0, 64))
                        .optional(
                                "droplets",
                                Schemas.integerBetween("Number of droplets (default 70000)", 1, 5_000_000))
                        .optional(
                                "max_lifetime",
                                Schemas.integerBetween("Max steps per droplet (default 30)", 1, 512))
                        .optional("inertia", Schemas.number("Direction inertia 0..1 (default 0.05)"))
                        .optional("capacity", Schemas.number("Sediment capacity factor (default 4.0)"))
                        .optional("deposition", Schemas.number("Deposition rate 0..1 (default 0.3)"))
                        .optional("erosion", Schemas.number("Erosion rate 0..1 (default 0.3)"))
                        .optional("evaporation", Schemas.number("Evaporation rate 0..1 (default 0.01)"))
                        .optional("gravity", Schemas.number("Gravity (default 4.0)"))
                        .optional("initial_speed", Schemas.number("Droplet initial speed (default 1.0)"))
                        .optional("initial_water", Schemas.number("Droplet initial water (default 1.0)"))
                        .optional("seed", Schemas.integer("RNG seed (default 0)"))
                        .optional(
                                "protect_box",
                                Schemas.object()
                                        .description("Region left uneroded (e.g. a built structure)")
                                        .required("x0", Schemas.integer("Min X"))
                                        .required("z0", Schemas.integer("Min Z"))
                                        .required("x1", Schemas.integer("Max X"))
                                        .required("z1", Schemas.integer("Max Z"))
                                        .build())
                        .optional(
                                "apron",
                                Schemas.integerBetween("Taper width around protect_box (default 0)", 0, 256))
                        .optional("dry_run", Schemas.bool("Compute only; do not write blocks (default false)"))
                        .build();

        public HydraulicErodeStart() {
            super("block_erode_hydraulic_start");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            var origin = r.requireObject("origin");
            int originX = origin.get("x").asInt();
            int originZ = origin.get("z").asInt();
            int width = r.requireInt("width");
            int length = r.requireInt("length");
            long columns = (long) width * length;
            if (columns > HARD_CAP) {
                throw new McpException(
                        ErrorCodes.TOOL_INPUT_INVALID,
                        "block_erode_hydraulic_start: " + columns + " columns exceed the hard cap "
                                + HARD_CAP + " (512x512); tile the region");
            }
            int floorY = r.requireInt("floor_y");
            String surface = r.optString("surface", "minecraft:grass_block");
            String subsurface = r.optString("subsurface", "minecraft:dirt");
            int subDepth = r.optInt("subsurface_depth", 3);
            int droplets = r.optInt("droplets", 70_000);
            int maxLifetime = r.optInt("max_lifetime", 30);
            double inertia = r.optDouble("inertia", 0.05);
            double capacity = r.optDouble("capacity", 4.0);
            double deposition = r.optDouble("deposition", 0.3);
            double erosion = r.optDouble("erosion", 0.3);
            double evaporation = r.optDouble("evaporation", 0.01);
            double gravity = r.optDouble("gravity", 4.0);
            double initialSpeed = r.optDouble("initial_speed", 1.0);
            double initialWater = r.optDouble("initial_water", 1.0);
            long seed = r.optLong("seed", 0L);
            int apron = r.optInt("apron", 0);
            boolean dryRun = r.optBoolean("dry_run", false);

            int px0 = Integer.MIN_VALUE;
            int pz0 = Integer.MIN_VALUE;
            int px1 = Integer.MIN_VALUE;
            int pz1 = Integer.MIN_VALUE;
            JsonNode protect = arguments.get("protect_box");
            if (protect != null && protect.isObject()) {
                px0 = protect.get("x0").asInt();
                pz0 = protect.get("z0").asInt();
                px1 = protect.get("x1").asInt();
                pz1 = protect.get("z1").asInt();
            }
            final int fpx0 = px0;
            final int fpz0 = pz0;
            final int fpx1 = px1;
            final int fpz1 = pz1;

            return onMainThread(
                    context,
                    ignored -> {
                        int[] heights =
                                context.adapter()
                                        .terrainSurveyHeights(dim, originX, originZ, width, length);
                        ErosionJob.Params params =
                                new ErosionJob.Params(
                                        dim, originX, originZ, width, length, floorY,
                                        surface, subsurface, subDepth,
                                        droplets, maxLifetime, inertia, capacity, deposition,
                                        erosion, evaporation, gravity, initialSpeed, initialWater,
                                        fpx0, fpz0, fpx1, fpz1, apron, seed, dryRun);
                        String jobId = context.jobs().nextJobId();
                        ErosionJob job = new ErosionJob(jobId, params, heights);
                        context.jobs().submit(job);
                        ObjectNode payload = context.mapper().createObjectNode();
                        payload.put("job_id", jobId);
                        payload.put("columns", (int) columns);
                        payload.put("state", job.state().name());
                        payload.put("dry_run", dryRun);
                        return ToolResult.ofToon(payload);
                    });
        }
    }

    @McpTool(
            name = "block_erode_hydraulic_status",
            description =
                    "Poll a hydraulic erosion job by job_id: returns state (ERODING/WRITING/DONE/FAILED),"
                            + " progress 0..1, columns written/total, blocks_changed so far, and any error."
                            + " Reads job state directly; does not touch the world.",
            readOnly = true)
    public static final class HydraulicErodeStatus extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("job_id", Schemas.string("Job id from block_erode_hydraulic_start"))
                        .build();

        public HydraulicErodeStatus() {
            super("block_erode_hydraulic_status");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String jobId = r.requireString("job_id");
            ErosionJob job = context.jobs().get(jobId);
            if (job == null) {
                throw new McpException(
                        ErrorCodes.TOOL_INPUT_INVALID,
                        "block_erode_hydraulic_status: unknown or evicted job_id '" + jobId + "'");
            }
            ObjectNode payload = context.mapper().createObjectNode();
            payload.put("job_id", jobId);
            payload.put("state", job.state().name());
            payload.put("progress", job.progress());
            payload.put("written", job.written());
            payload.put("total", job.total());
            payload.put("blocks_changed", job.blocksChanged());
            if (job.error() != null) {
                payload.put("error", job.error());
            }
            return ToolResult.ofToon(payload);
        }
    }

    @McpTool(
            name = "block_erode_hydraulic_result",
            description =
                    "Read the final result of a hydraulic erosion job by job_id: state, blocks_changed,"
                            + " max_delta, mean_abs_delta, moved, columns, dry_run. On a dry_run job it"
                            + " also returns heights: the flat row-major (xi*length+zi) eroded new-height"
                            + " grid for offline render-verify before applying. Call once state is DONE.",
            readOnly = true)
    public static final class HydraulicErodeResult extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("job_id", Schemas.string("Job id from block_erode_hydraulic_start"))
                        .build();

        public HydraulicErodeResult() {
            super("block_erode_hydraulic_result");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String jobId = r.requireString("job_id");
            ErosionJob job = context.jobs().get(jobId);
            if (job == null) {
                throw new McpException(
                        ErrorCodes.TOOL_INPUT_INVALID,
                        "block_erode_hydraulic_result: unknown or evicted job_id '" + jobId + "'");
            }
            ObjectNode payload = context.mapper().createObjectNode();
            payload.put("job_id", jobId);
            payload.put("state", job.state().name());
            payload.put("blocks_changed", job.blocksChanged());
            payload.put("max_delta", job.maxDelta());
            payload.put("mean_abs_delta", job.meanAbsDelta());
            payload.put("moved", job.movedTotal());
            payload.put("columns", job.total());
            payload.put("dry_run", job.params().dryRun());
            // Dry run returns the flat row-major (xi*length+zi) eroded height grid so the
            // offline client can render-verify the proposal before applying. On an apply
            // the grid is omitted (it would bloat the response and is already in-world).
            int[] heights = job.newHeights();
            if (job.params().dryRun() && heights != null) {
                ArrayNode hs = payload.putArray("heights");
                for (int hv : heights) {
                    hs.add(hv);
                }
            }
            if (job.error() != null) {
                payload.put("error", job.error());
            }
            return ToolResult.ofToon(payload);
        }
    }

    private static int[] readIntArray(JsonNode arr, String name, int expectedLen) {
        if (arr.size() != expectedLen) {
            throw new McpException(
                    ErrorCodes.TOOL_INPUT_INVALID,
                    "block_fill_columns: '" + name + "' length " + arr.size() + " != width*length " + expectedLen);
        }
        int[] out = new int[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            JsonNode e = arr.get(i);
            if (e == null || !e.isIntegralNumber() || !e.canConvertToInt()) {
                throw new McpException(
                        ErrorCodes.TOOL_INPUT_INVALID, "block_fill_columns: '" + name + "[" + i + "]' must be an integer");
            }
            out[i] = e.intValue();
        }
        return out;
    }

    private static java.util.List<String> readStringArray(JsonNode arr, String name) {
        java.util.List<String> out = new java.util.ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            JsonNode e = arr.get(i);
            if (e == null || !e.isTextual()) {
                throw new McpException(
                        ErrorCodes.TOOL_INPUT_INVALID, "block_fill_columns: '" + name + "[" + i + "]' must be a string");
            }
            out.add(e.asText());
        }
        return out;
    }

    // -------------------------------------------------------------------
    // block_scan_summary
    // -------------------------------------------------------------------
    @McpTool(
            name = "block_scan_summary",
            description =
                    "Aggregate scan of a box: non-air count, the non-air bounding box, and a material"
                            + " histogram — computed server-side so no raw per-block data floods context."
                            + " The primitive for archaeology (scan a high y-layer, cluster materials) and"
                            + " pre-clear checks (what would I overwrite?). Volume capped at 1,048,576.")
    public static final class ScanSummary extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("box", Schemas.box3d("Bounding box (volume <= 1048576)"))
                        .optional(
                                "top",
                                Schemas.integerBetween("Max histogram entries to return", 1, 512))
                        .build();

        public ScanSummary() {
            super("block_scan_summary");
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
            int top = r.optInt("top", 64);
            return onMainThread(
                    context,
                    ignored -> {
                        var s = context.adapter().blockScanSummary(dim, box);
                        ObjectNode payload = context.mapper().createObjectNode();
                        payload.put("scanned_volume", s.scannedVolume());
                        payload.put("non_air", s.nonAirCount());
                        if (s.nonAirMin() != null) {
                            ObjectNode bounds = payload.putObject("non_air_bounds");
                            bounds.set("min", Jsons.vec3i(context.mapper(), s.nonAirMin()));
                            bounds.set("max", Jsons.vec3i(context.mapper(), s.nonAirMax()));
                        }
                        ArrayNode hist = payload.putArray("histogram");
                        s.histogram().entrySet().stream()
                                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                                .limit(top)
                                .forEach(
                                        e -> {
                                            ObjectNode n = hist.addObject();
                                            n.put("id", e.getKey());
                                            n.put("count", e.getValue());
                                        });
                        return ToolResult.ofToon(payload);
                    });
        }
    }

    // -------------------------------------------------------------------
    // block_get_map_color
    // -------------------------------------------------------------------
    @McpTool(
            name = "block_get_map_color",
            description =
                    "Returns the base map colour of the block at a position — packed rgb (0xRRGGBB), a"
                            + " #RRGGBB hex string, r/g/b components, and the palette id. The authoritative"
                            + " block-to-colour mapping for rendering and pixel-art quantization.")
    public static final class GetMapColor extends BaseTool {
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

        public GetMapColor() {
            super("block_get_map_color");
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
                        var info =
                                context.adapter()
                                        .blockGetMapColor(dim, pos)
                                        .orElseThrow(
                                                () ->
                                                        new McpException(
                                                                ErrorCodes.TOOL_HANDLER_ERROR,
                                                                "Block position not loaded"));
                        int rgb = info.rgb() & 0xFFFFFF;
                        ObjectNode payload = context.mapper().createObjectNode();
                        payload.put("id", info.id());
                        payload.put("rgb", rgb);
                        payload.put("hex", String.format(java.util.Locale.ROOT, "#%06X", rgb));
                        payload.put("r", (rgb >> 16) & 0xFF);
                        payload.put("g", (rgb >> 8) & 0xFF);
                        payload.put("b", rgb & 0xFF);
                        return ToolResult.ofToon(payload);
                    });
        }
    }

    // -------------------------------------------------------------------
    // block_render_region
    // -------------------------------------------------------------------
    @McpTool(
            name = "block_render_region",
            description =
                    "Render a region to a PNG you can SEE — the verify-time eyes for representational"
                            + " builds. Flat-shaded voxel render from block map colours, server-side (no"
                            + " client needed). view: iso (default) | side | front | top | hillshade"
                            + " (relief-shaded plan view for TERRAIN — terraces/ziggurats show as flat"
                            + " bands; span the full vertical extent). step downsamples (1 = every block)"
                            + " to fit large regions; scale = pixels per voxel. Sampled cells (after step)"
                            + " capped at 4,194,304 — raise step or shrink the box.")
    public static final class RenderRegion extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("box", Schemas.box3d("Region to render"))
                        .optional("view", Schemas.enumOf("Projection", "iso", "side", "front", "top", "hillshade"))
                        .optional("step", Schemas.integerBetween("Downsample stride (1 = full res)", 1, 16))
                        .optional("scale", Schemas.integerBetween("Pixels per voxel", 1, 16))
                        .build();

        public RenderRegion() {
            super("block_render_region");
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
            String view = r.optString("view", "iso");
            int step = r.optInt("step", 1);
            int scale = r.optInt("scale", 4);
            return onMainThread(
                    context,
                    ignored -> {
                        byte[] png = context.adapter().worldRenderRegion(dim, box, view, step, scale);
                        return ToolResult.ofImage(png, "image/png");
                    });
        }
    }
}
