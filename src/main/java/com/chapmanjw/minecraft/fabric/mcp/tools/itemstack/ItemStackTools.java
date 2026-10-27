package com.chapmanjw.minecraft.fabric.mcp.tools.itemstack;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
                            + " Validates that the item id exists. componentKeys lists the item's data"
                            + " components; pass include_values=true to also get each component's"
                            + " value as JSON (the form the game saves), optionally narrowed with"
                            + " component_filter. Values can be large, so ask only when needed.")
    public static final class Describe extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("id", Schemas.string("Item id"))
                        .optional("count", Schemas.integer("Count (default 1)"))
                        .optional("components", Schemas.string("Components SNBT"))
                        .optional(
                                "include_values",
                                Schemas.bool("Also return component values as JSON (default false)"))
                        .optional(
                                "component_filter",
                                Schemas.arrayOf(
                                        "Only these component ids, e.g. [\"minecraft:cooking_fuel\"];"
                                                + " implies include_values",
                                        Schemas.string()))
                        .build();

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
            List<String> filter = new java.util.ArrayList<>();
            JsonNode filterNode = r.optArray("component_filter");
            if (filterNode != null) {
                for (JsonNode f : filterNode) {
                    filter.add(f.asText());
                }
            }
            boolean includeValues = r.optBoolean("include_values", false) || !filter.isEmpty();
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
                        ObjectNode out = Jsons.itemStack(context.mapper(), info);
                        if (includeValues) {
                            ObjectNode values = out.putObject("components");
                            var raw = context.adapter().itemStackComponentValues(spec, filter).orElseThrow();
                            for (var e : raw.entrySet()) {
                                if (e.getValue() == null) {
                                    values.putNull(e.getKey());
                                } else {
                                    try {
                                        values.set(e.getKey(), context.mapper().readTree(e.getValue()));
                                    } catch (java.io.IOException io) {
                                        values.put(e.getKey(), e.getValue());
                                    }
                                }
                            }
                        }
                        return ToolResult.ofToon(out);
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
