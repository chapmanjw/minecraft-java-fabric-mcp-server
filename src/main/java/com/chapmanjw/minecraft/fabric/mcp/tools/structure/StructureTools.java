package com.chapmanjw.minecraft.fabric.mcp.tools.structure;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BoundingBox;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.StructureInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Structure (StructureTemplate) tools. */
public final class StructureTools {

    private StructureTools() {}

    private static Vec3i readVec3i(JsonNode n) {
        return new Vec3i(n.get("x").asInt(), n.get("y").asInt(), n.get("z").asInt());
    }

    private static BoundingBox readBox(JsonNode n) {
        return BoundingBox.of(readVec3i(n.get("from")), readVec3i(n.get("to")));
    }

    @McpTool(
            name = "structure_save_from_world",
            description = "Captures a region of the world into a saved structure template.")
    public static final class SaveFromWorld extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("name", Schemas.string("Structure name"))
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("box", Schemas.box3d("Bounding box to capture"))
                        .optional("include_entities", Schemas.bool("Capture entities (default false)"))
                        .build();

        public SaveFromWorld() {
            super("structure_save_from_world");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String name = r.requireString("name");
            String dim = r.requireString("dimension");
            BoundingBox box = readBox(r.requireObject("box"));
            boolean ents = r.optBoolean("include_entities", false);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().structureSaveFromWorld(name, dim, box, ents)
                                            ? "saved " + name
                                            : "failed"));
        }
    }

    @McpTool(
            name = "structure_load_to_world",
            description = "Places a saved structure into the world with optional rotation/mirror.")
    public static final class LoadToWorld extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("name", Schemas.string("Structure name"))
                        .required("dimension", Schemas.string("Destination dimension"))
                        .required(
                                "origin",
                                Schemas.object()
                                        .required("x", Schemas.integer("X"))
                                        .required("y", Schemas.integer("Y"))
                                        .required("z", Schemas.integer("Z"))
                                        .build())
                        .optional(
                                "rotation",
                                Schemas.enumOf(
                                        "Rotation",
                                        "none",
                                        "clockwise_90",
                                        "180",
                                        "counterclockwise_90"))
                        .optional(
                                "mirror",
                                Schemas.enumOf("Mirror axis", "none", "front_back", "left_right"))
                        .optional("include_entities", Schemas.bool("Place entities (default false)"))
                        .optional("integrity", Schemas.number("Random-decay factor 0..1 (default 1.0)"))
                        .build();

        public LoadToWorld() {
            super("structure_load_to_world");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String name = r.requireString("name");
            String dim = r.requireString("dimension");
            Vec3i origin = readVec3i(r.requireObject("origin"));
            String rot = r.optString("rotation", "none");
            String mir = r.optString("mirror", "none");
            boolean ents = r.optBoolean("include_entities", false);
            float integ = (float) r.optDouble("integrity", 1.0);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                                    .structureLoadToWorld(
                                                            name, dim, origin, rot, mir, ents, integ)
                                            ? "placed " + name
                                            : "failed"));
        }
    }

    @McpTool(
            name = "structure_list",
            description = "Lists every saved structure (in-memory and on-disk). Paginated: pass `offset`/`limit`; response includes `total` and `next_offset`.")
    public static final class ListAll extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .optional("offset", Schemas.integer("0-based offset (default 0)."))
                        .optional(
                                "limit",
                                Schemas.integerBetween(
                                        "Max structures returned (default 200, max 2000).", 1, 2000))
                        .build();

        public ListAll() {
            super("structure_list");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            int offset = Math.max(0, r.optInt("offset", 0));
            int limit = Math.max(1, Math.min(2000, r.optInt("limit", 200)));
            return onMainThread(
                    context,
                    ignored -> {
                        List<StructureInfo> list = context.adapter().structureList();
                        int total = list.size();
                        int from = Math.min(offset, total);
                        int to = Math.min(from + limit, total);
                        ObjectNode payload = context.mapper().createObjectNode();
                        ArrayNode items = payload.putArray("items");
                        for (int i = from; i < to; i++) {
                            StructureInfo s = list.get(i);
                            ObjectNode n = items.addObject();
                            n.put("name", s.name());
                            n.put("sizeX", s.sizeX());
                            n.put("sizeY", s.sizeY());
                            n.put("sizeZ", s.sizeZ());
                            n.put("fileSizeBytes", s.fileSizeBytes());
                            n.put("onDisk", s.onDisk());
                            n.put("inMemory", s.inMemory());
                        }
                        payload.put("total", total);
                        if (to < total) {
                            payload.put("next_offset", to);
                        } else {
                            payload.putNull("next_offset");
                        }
                        return ToolResult.ofToon(payload);
                    });
        }
    }

    @McpTool(name = "structure_get_info", description = "Returns metadata for a saved structure.")
    public static final class GetInfo extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("name", Schemas.string("Structure name")).build();

        public GetInfo() {
            super("structure_get_info");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String name = reader(arguments).requireString("name");
            return onMainThread(
                    context,
                    ignored -> {
                        StructureInfo s =
                                context.adapter()
                                        .structureGetInfo(name)
                                        .orElseThrow(
                                                () ->
                                                        new McpException(
                                                                ErrorCodes.TOOL_HANDLER_ERROR,
                                                                "Unknown structure: " + name));
                        ObjectNode n = context.mapper().createObjectNode();
                        n.put("name", s.name());
                        n.put("sizeX", s.sizeX());
                        n.put("sizeY", s.sizeY());
                        n.put("sizeZ", s.sizeZ());
                        n.put("fileSizeBytes", s.fileSizeBytes());
                        n.put("onDisk", s.onDisk());
                        n.put("inMemory", s.inMemory());
                        return ToolResult.ofToon(n);
                    });
        }
    }

    @McpTool(name = "structure_delete", description = "Deletes a saved structure.")
    public static final class Delete extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("name", Schemas.string("Structure name")).build();

        public Delete() {
            super("structure_delete");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String name = reader(arguments).requireString("name");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().structureDelete(name) ? "deleted" : "failed"));
        }
    }

    @McpTool(name = "structure_file_read", description = "Reads a structure file as base64 bytes.")
    public static final class FileRead extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("name", Schemas.string("Structure file name")).build();

        public FileRead() {
            super("structure_file_read");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String name = reader(arguments).requireString("name");
            return onMainThread(
                    context,
                    ignored -> {
                        byte[] bytes = context.adapter().structureFileRead(name);
                        if (bytes == null) {
                            throw new McpException(
                                    ErrorCodes.TOOL_HANDLER_ERROR, "Structure file not found: " + name);
                        }
                        return ToolResult.ofText(Base64.getEncoder().encodeToString(bytes));
                    });
        }
    }

    @McpTool(name = "structure_file_write", description = "Writes a structure file from base64 bytes.")
    public static final class FileWrite extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("name", Schemas.string("Structure file name"))
                        .required("payload_base64", Schemas.string("Base64-encoded structure file"))
                        .build();

        public FileWrite() {
            super("structure_file_write");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String name = r.requireString("name");
            byte[] payload =
                    Base64.getDecoder()
                            .decode(r.requireString("payload_base64").getBytes(StandardCharsets.UTF_8));
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().structureFileWrite(name, payload)
                                            ? "written"
                                            : "failed"));
        }
    }

    @McpTool(name = "structure_file_list", description = "Lists every structure file on disk.")
    public static final class FileList extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

        public FileList() {
            super("structure_file_list");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            return onMainThread(
                    context,
                    ignored -> {
                        var list = context.adapter().structureFileList();
                        ArrayNode arr = context.mapper().createArrayNode();
                        list.forEach(arr::add);
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(name = "structure_file_delete", description = "Deletes a structure file from disk.")
    public static final class FileDelete extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("name", Schemas.string("Structure file name")).build();

        public FileDelete() {
            super("structure_file_delete");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String name = reader(arguments).requireString("name");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().structureFileDelete(name) ? "deleted" : "failed"));
        }
    }
}
