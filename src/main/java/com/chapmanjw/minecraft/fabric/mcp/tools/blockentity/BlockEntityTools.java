package com.chapmanjw.minecraft.fabric.mcp.tools.blockentity;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Block entity tools — chest contents, sign text, etc. */
public final class BlockEntityTools {

    private BlockEntityTools() {}

    private static Vec3i readPos(JsonNode node) {
        return new Vec3i(node.get("x").asInt(), node.get("y").asInt(), node.get("z").asInt());
    }

    private static JsonNode posBoxSchema() {
        return Schemas.object()
                .required("x", Schemas.integer("X"))
                .required("y", Schemas.integer("Y"))
                .required("z", Schemas.integer("Z"))
                .build();
    }

    @McpTool(
            name = "block_entity_get_nbt",
            description = "Returns the full NBT (as SNBT) of the block entity at a position.")
    public static final class GetNbt extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("position", posBoxSchema())
                        .build();

        public GetNbt() {
            super("block_entity_get_nbt");
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
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                            .blockEntityGetNbt(dim, pos)
                                            .orElseThrow(
                                                    () ->
                                                            new McpException(
                                                                    ErrorCodes.TOOL_HANDLER_ERROR,
                                                                    "No block entity at that position"))));
        }
    }

    @McpTool(
            name = "block_entity_set_nbt",
            description = "Merges the supplied SNBT into the block entity at a position (vanilla /data merge).")
    public static final class SetNbt extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("position", posBoxSchema())
                        .required("nbt", Schemas.string("SNBT to merge"))
                        .build();

        public SetNbt() {
            super("block_entity_set_nbt");
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
            String snbt = r.requireString("nbt");
            return onMainThread(
                    context,
                    ignored -> {
                        boolean ok = context.adapter().blockEntitySetNbt(dim, pos, snbt);
                        return ToolResult.ofText(ok ? "merged" : "no change");
                    });
        }
    }

    @McpTool(name = "block_entity_clear_inventory", description = "Clears a container block's contents.")
    public static final class ClearInventory extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("position", posBoxSchema())
                        .build();

        public ClearInventory() {
            super("block_entity_clear_inventory");
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
                        boolean ok = context.adapter().blockEntityClearInventory(dim, pos);
                        return ToolResult.ofText(ok ? "cleared" : "no block entity");
                    });
        }
    }
}
