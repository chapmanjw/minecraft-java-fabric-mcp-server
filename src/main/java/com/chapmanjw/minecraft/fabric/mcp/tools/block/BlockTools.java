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
                            + " client needed). view: iso (default) | side | front | top. step downsamples"
                            + " (1 = every block) to fit large regions; scale = pixels per voxel. Sampled"
                            + " cells (after step) capped at 4,194,304 — raise step or shrink the box.")
    public static final class RenderRegion extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("box", Schemas.box3d("Region to render"))
                        .optional("view", Schemas.enumOf("Projection", "iso", "side", "front", "top"))
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
