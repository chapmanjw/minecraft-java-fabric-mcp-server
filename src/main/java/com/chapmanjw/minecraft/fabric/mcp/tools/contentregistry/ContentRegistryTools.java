package com.chapmanjw.minecraft.fabric.mcp.tools.contentregistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CompostableInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.FlammableBlockInfo;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/**
 * Fabric content-registry wrappers: fuel burn time, flammable-block parameters,
 * and composter level-up chance.
 *
 * <p>Reads always succeed against the running registry data; runtime mutation is
 * supported for flammable and compostable entries — fuel values are immutable at
 * runtime because Fabric only exposes them via build-time events.
 */
public final class ContentRegistryTools {

    private ContentRegistryTools() {}

    @McpTool(
            name = "content_registry_get_fuel",
            description = "Returns the burn time (ticks) for an item; 0 if the item is not a fuel.",
            requiredFabricModules = {"fabric-content-registries-v0"})
    public static final class GetFuel extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("item_id", Schemas.string("Item identifier")).build();

        public GetFuel() {
            super("content_registry_get_fuel");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String id = reader(arguments).requireString("item_id");
            return onMainThread(
                    context,
                    ignored -> {
                        int burn = context.adapter().contentRegistryGetFuel(id);
                        ObjectNode n = context.mapper().createObjectNode();
                        n.put("burn_time_ticks", burn);
                        return ToolResult.ofToon(n);
                    });
        }
    }

    @McpTool(
            name = "content_registry_set_fuel",
            description =
                    "Registers or overrides a fuel's burn time. NOTE: runtime mutation is not"
                            + " supported in current Fabric — call is recorded but no-op.",
            requiredFabricModules = {"fabric-content-registries-v0"})
    public static final class SetFuel extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("item_id", Schemas.string("Item identifier"))
                        .required("burn_time_ticks", Schemas.integer("Burn time in ticks"))
                        .build();

        public SetFuel() {
            super("content_registry_set_fuel");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("item_id");
            int burn = r.requireInt("burn_time_ticks");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().contentRegistrySetFuel(id, burn)
                                            ? "set"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "content_registry_is_flammable_block",
            description = "Returns flammability parameters for a block.",
            requiredFabricModules = {"fabric-content-registries-v0"})
    public static final class IsFlammableBlock extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("block_id", Schemas.string("Block identifier")).build();

        public IsFlammableBlock() {
            super("content_registry_is_flammable_block");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String id = reader(arguments).requireString("block_id");
            return onMainThread(
                    context,
                    ignored -> {
                        FlammableBlockInfo info = context.adapter().contentRegistryGetFlammableBlock(id);
                        ObjectNode n = context.mapper().createObjectNode();
                        n.put("flammable", info.flammable());
                        n.put("spread_chance", info.spreadChance());
                        n.put("burn_chance", info.burnChance());
                        return ToolResult.ofToon(n);
                    });
        }
    }

    @McpTool(
            name = "content_registry_set_flammable_block",
            description = "Registers or overrides a block's flammability parameters.",
            requiredFabricModules = {"fabric-content-registries-v0"})
    public static final class SetFlammableBlock extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("block_id", Schemas.string("Block identifier"))
                        .required("burn_chance", Schemas.integer("Burn chance"))
                        .required("spread_chance", Schemas.integer("Fire spread chance"))
                        .build();

        public SetFlammableBlock() {
            super("content_registry_set_flammable_block");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("block_id");
            int burn = r.requireInt("burn_chance");
            int spread = r.requireInt("spread_chance");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().contentRegistrySetFlammableBlock(id, burn, spread)
                                            ? "set"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "content_registry_is_compostable",
            description = "Returns the composter level-up chance for an item.",
            requiredFabricModules = {"fabric-content-registries-v0"})
    public static final class IsCompostable extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("item_id", Schemas.string("Item identifier")).build();

        public IsCompostable() {
            super("content_registry_is_compostable");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String id = reader(arguments).requireString("item_id");
            return onMainThread(
                    context,
                    ignored -> {
                        CompostableInfo info = context.adapter().contentRegistryGetCompostable(id);
                        ObjectNode n = context.mapper().createObjectNode();
                        n.put("compostable", info.compostable());
                        n.put("chance", info.chance());
                        return ToolResult.ofToon(n);
                    });
        }
    }

    @McpTool(
            name = "content_registry_set_compostable",
            description = "Registers or overrides an item's composter level-up chance (0.0 to 1.0).",
            requiredFabricModules = {"fabric-content-registries-v0"})
    public static final class SetCompostable extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("item_id", Schemas.string("Item identifier"))
                        .required("chance", Schemas.number("Level-up chance, 0.0 to 1.0"))
                        .build();

        public SetCompostable() {
            super("content_registry_set_compostable");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("item_id");
            float chance = (float) r.requireDouble("chance");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().contentRegistrySetCompostable(id, chance)
                                            ? "set"
                                            : "failed"));
        }
    }
}
