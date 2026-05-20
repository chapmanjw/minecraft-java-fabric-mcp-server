package com.chapmanjw.minecraft.fabric.mcp.tools.inventory;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ItemSpec;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.Jsons;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/**
 * Generic container tools. A {@code target} string identifies the container —
 * {@code player:<uuid>}, {@code entity:<uuid>}, or {@code block:<dim>:<x>:<y>:<z>}.
 */
public final class InventoryTools {

    private InventoryTools() {}

    private static JsonNode targetSchema() {
        return Schemas.string(
                "Container target. Format: player:<uuid> | entity:<uuid> | block:<dim>:<x>:<y>:<z>");
    }

    @McpTool(name = "inventory_get", description = "Reads a container's contents.")
    public static final class Get extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("target", targetSchema()).build();

        public Get() {
            super("inventory_get");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String target = reader(arguments).requireString("target");
            return onMainThread(
                    context,
                    ignored -> {
                        var inv =
                                context.adapter()
                                        .inventoryGet(target)
                                        .orElseThrow(
                                                () ->
                                                        new McpException(
                                                                ErrorCodes.TOOL_HANDLER_ERROR,
                                                                "Container not found: " + target));
                        return ToolResult.ofToon(Jsons.inventory(context.mapper(), inv));
                    });
        }
    }

    @McpTool(name = "inventory_set_slot", description = "Sets a single container slot to an item stack.")
    public static final class SetSlot extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("target", targetSchema())
                        .required("slot", Schemas.integer("Slot index"))
                        .required(
                                "item",
                                Schemas.object()
                                        .required("id", Schemas.string("Item id"))
                                        .optional("count", Schemas.integer("Count (default 1)"))
                                        .optional("components", Schemas.string("Components SNBT"))
                                        .build())
                        .build();

        public SetSlot() {
            super("inventory_set_slot");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String target = r.requireString("target");
            int slot = r.requireInt("slot");
            JsonNode it = r.requireObject("item");
            ItemSpec item =
                    new ItemSpec(
                            it.get("id").asText(),
                            it.has("count") ? it.get("count").asInt() : 1,
                            it.has("components") ? it.get("components").asText() : null);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().inventorySetSlot(target, slot, item) ? "set" : "failed"));
        }
    }

    @McpTool(name = "inventory_clear_slot", description = "Clears a single container slot.")
    public static final class ClearSlot extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("target", targetSchema())
                        .required("slot", Schemas.integer("Slot index"))
                        .build();

        public ClearSlot() {
            super("inventory_clear_slot");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String target = r.requireString("target");
            int slot = r.requireInt("slot");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().inventoryClearSlot(target, slot) ? "cleared" : "failed"));
        }
    }

    @McpTool(name = "inventory_swap_slots", description = "Swaps the contents of two container slots.")
    public static final class SwapSlots extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("target", targetSchema())
                        .required("slot_a", Schemas.integer("First slot"))
                        .required("slot_b", Schemas.integer("Second slot"))
                        .build();

        public SwapSlots() {
            super("inventory_swap_slots");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String target = r.requireString("target");
            int a = r.requireInt("slot_a");
            int b = r.requireInt("slot_b");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().inventorySwapSlots(target, a, b) ? "swapped" : "failed"));
        }
    }

    @McpTool(
            name = "inventory_count_items",
            description = "Counts total stacks of a given item id across a container.")
    public static final class CountItems extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("target", targetSchema())
                        .required("item_id", Schemas.string("Item id to count"))
                        .build();

        public CountItems() {
            super("inventory_count_items");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String target = r.requireString("target");
            String id = r.requireString("item_id");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    String.valueOf(context.adapter().inventoryCountItems(target, id))));
        }
    }
}
