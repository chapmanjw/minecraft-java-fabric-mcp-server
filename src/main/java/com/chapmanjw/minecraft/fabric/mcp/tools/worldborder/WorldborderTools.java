package com.chapmanjw.minecraft.fabric.mcp.tools.worldborder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.WorldBorderInfo;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Vanilla {@code /worldborder} command surface. */
public final class WorldborderTools {

    private WorldborderTools() {}

    private static JsonNode dimensionRequired() {
        return Schemas.string("Dimension identifier");
    }

    @McpTool(name = "worldborder_get", description = "Returns the world border settings for a dimension.")
    public static final class Get extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("dimension", dimensionRequired()).build();

        public Get() {
            super("worldborder_get");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String dim = reader(arguments).requireString("dimension");
            return onMainThread(
                    context,
                    ignored -> {
                        WorldBorderInfo info = context.adapter().worldborderGet(dim);
                        ObjectNode n = context.mapper().createObjectNode();
                        n.put("center_x", info.centerX());
                        n.put("center_z", info.centerZ());
                        n.put("size", info.size());
                        n.put("warning_blocks", info.warningBlocks());
                        n.put("warning_seconds", info.warningSeconds());
                        n.put("damage_per_block", info.damagePerBlock());
                        n.put("safe_zone", info.safeZone());
                        if (info.lerpTimeRemainingTicks() >= 0) {
                            n.put("lerp_target", info.lerpTarget());
                            n.put("lerp_time_remaining_ticks", info.lerpTimeRemainingTicks());
                        }
                        return ToolResult.ofToon(n);
                    });
        }
    }

    @McpTool(name = "worldborder_set_size", description = "Sets the world border size, optionally over time.")
    public static final class SetSize extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", dimensionRequired())
                        .required("size", Schemas.number("New size"))
                        .optional("time_seconds", Schemas.integer("Transition time"))
                        .build();

        public SetSize() {
            super("worldborder_set_size");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            double size = r.requireDouble("size");
            int time = r.optInt("time_seconds", 0);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().worldborderSetSize(dim, size, time)
                                            ? "set"
                                            : "failed"));
        }
    }

    @McpTool(name = "worldborder_add_size", description = "Adds a delta to the current world border size.")
    public static final class AddSize extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", dimensionRequired())
                        .required("delta", Schemas.number("Delta to add"))
                        .optional("time_seconds", Schemas.integer("Transition time"))
                        .build();

        public AddSize() {
            super("worldborder_add_size");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            double delta = r.requireDouble("delta");
            int time = r.optInt("time_seconds", 0);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().worldborderAddSize(dim, delta, time)
                                            ? "added"
                                            : "failed"));
        }
    }

    @McpTool(name = "worldborder_set_center", description = "Sets the world border center.")
    public static final class SetCenter extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", dimensionRequired())
                        .required("x", Schemas.number("Center X"))
                        .required("z", Schemas.number("Center Z"))
                        .build();

        public SetCenter() {
            super("worldborder_set_center");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                                    .worldborderSetCenter(
                                                            r.requireString("dimension"),
                                                            r.requireDouble("x"),
                                                            r.requireDouble("z"))
                                            ? "set"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "worldborder_set_warning_blocks",
            description = "Sets the warning distance in blocks.")
    public static final class SetWarningBlocks extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", dimensionRequired())
                        .required("blocks", Schemas.integer("Warning blocks"))
                        .build();

        public SetWarningBlocks() {
            super("worldborder_set_warning_blocks");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                                    .worldborderSetWarningBlocks(
                                                            r.requireString("dimension"),
                                                            r.requireInt("blocks"))
                                            ? "set"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "worldborder_set_warning_time",
            description = "Sets the warning time in seconds.")
    public static final class SetWarningTime extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", dimensionRequired())
                        .required("seconds", Schemas.integer("Warning seconds"))
                        .build();

        public SetWarningTime() {
            super("worldborder_set_warning_time");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                                    .worldborderSetWarningTime(
                                                            r.requireString("dimension"),
                                                            r.requireInt("seconds"))
                                            ? "set"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "worldborder_set_damage_amount",
            description = "Sets the damage per block dealt to players outside the border.")
    public static final class SetDamageAmount extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", dimensionRequired())
                        .required("amount", Schemas.number("Damage per block"))
                        .build();

        public SetDamageAmount() {
            super("worldborder_set_damage_amount");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                                    .worldborderSetDamageAmount(
                                                            r.requireString("dimension"),
                                                            r.requireDouble("amount"))
                                            ? "set"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "worldborder_set_damage_buffer",
            description = "Sets the safe-zone (damage buffer) distance outside the border.")
    public static final class SetDamageBuffer extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", dimensionRequired())
                        .required("buffer", Schemas.number("Safe-zone buffer"))
                        .build();

        public SetDamageBuffer() {
            super("worldborder_set_damage_buffer");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                                    .worldborderSetDamageBuffer(
                                                            r.requireString("dimension"),
                                                            r.requireDouble("buffer"))
                                            ? "set"
                                            : "failed"));
        }
    }
}
