package com.chapmanjw.minecraft.fabric.mcp.tools.itemstack;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ItemSpec;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ItemStackInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3d;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.Jsons;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Item stack tools. */
public final class ItemStackTools {

    private ItemStackTools() {}

    private static JsonNode itemSpecSchema() {
        return Schemas.object()
                .required("id", Schemas.string("Item id"))
                .optional("count", Schemas.integer("Count (default 1)"))
                .optional("components", Schemas.string("Components SNBT"))
                .build();
    }

    @McpTool(
            name = "itemstack_describe",
            description =
                    "Returns what an ItemStack would look like — max stack size, max durability, etc."
                            + " Validates that the item id exists.")
    public static final class Describe extends BaseTool {
        private static final JsonNode SCHEMA = itemSpecSchema();

        public Describe() {
            super("itemstack_describe");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("id");
            int count = r.optInt("count", 1);
            String comps = r.optString("components", null);
            ItemSpec spec = new ItemSpec(id, count, comps);
            return onMainThread(
                    context,
                    ignored -> {
                        ItemStackInfo info =
                                context.adapter()
                                        .itemStackDescribe(spec)
                                        .orElseThrow(
                                                () ->
                                                        new McpException(
                                                                ErrorCodes.TOOL_HANDLER_ERROR,
                                                                "Unknown item: " + id));
                        return ToolResult.ofToon(Jsons.itemStack(context.mapper(), info));
                    });
        }
    }

    @McpTool(name = "itemstack_drop_at", description = "Spawns a dropped-item entity at a position.")
    public static final class DropAt extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("position", Schemas.position3d("World position"))
                        .required("item", itemSpecSchema())
                        .build();

        public DropAt() {
            super("itemstack_drop_at");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            JsonNode pos = r.requireObject("position");
            Vec3d p = new Vec3d(pos.get("x").asDouble(), pos.get("y").asDouble(), pos.get("z").asDouble());
            JsonNode it = r.requireObject("item");
            ItemSpec spec =
                    new ItemSpec(
                            it.get("id").asText(),
                            it.has("count") ? it.get("count").asInt() : 1,
                            it.has("components") ? it.get("components").asText() : null);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().itemStackDropAt(dim, p, spec) ? "dropped" : "failed"));
        }
    }
}
