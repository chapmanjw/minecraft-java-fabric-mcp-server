package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CommandResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/**
 * Grows many vanilla worldgen features in ONE call — the batch form of
 * {@code level_place_feature}. Each entry is a configured-feature id plus a
 * position; the whole list is placed inside a single main-thread submission, so a
 * dense vegetation scatter (hundreds–thousands of trees) costs one MCP round-trip
 * and one rate-limit slot instead of one per feature.
 *
 * <p>This is the throughput path for the terrain toolkit's scatter pass: it emits
 * a placement list of (feature, x, y, z) and hands it here rather than issuing
 * thousands of {@code level_place_feature} calls (~60/min would take hours).
 *
 * <p>Entries are capped at 4096 per call to stay well within the main-thread
 * timeout — tile larger scatters across calls. Per-entry success is reported so
 * the caller can retry only the failures.
 */
@McpTool(
        name = "level_place_features_batch",
        description =
                "Grows many vanilla worldgen features in ONE call (batch level_place_feature) —"
                        + " the throughput path for vegetation/detail scatter. Args: dimension,"
                        + " features[] of {feature, x, y, z}, optional stop_on_error. Each feature is a"
                        + " configured-feature id (e.g. minecraft:fancy_oak, minecraft:spruce). Capped at"
                        + " 4096 entries/call; tile larger scatters.")
public final class LevelPlaceFeaturesBatchTool extends BaseTool {

    /** Per-call entry cap — keeps the single main-thread submission under the timeout. */
    private static final int MAX_ENTRIES = 4096;

    private static final JsonNode SCHEMA =
            Schemas.object()
                    .required("dimension", Schemas.string("Dimension identifier, e.g. minecraft:overworld"))
                    .required(
                            "features",
                            Schemas.arrayOf(
                                    "Features to place, each {feature, x, y, z}; max 4096 entries",
                                    Schemas.object()
                                            .required(
                                                    "feature",
                                                    Schemas.string(
                                                            "Configured feature id, e.g. minecraft:fancy_oak"))
                                            .required("x", Schemas.integer("Block X"))
                                            .required("y", Schemas.integer("Block Y"))
                                            .required("z", Schemas.integer("Block Z"))
                                            .build()))
                    .optional(
                            "stop_on_error",
                            Schemas.bool("Stop at the first failed placement (default false)"))
                    .build();

    public LevelPlaceFeaturesBatchTool() {
        super("level_place_features_batch");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        var r = reader(arguments);
        String dim = r.requireString("dimension");
        JsonNode features = r.requireArray("features");
        boolean stopOnError = r.optBoolean("stop_on_error", false);

        if (features.size() > MAX_ENTRIES) {
            throw new McpException(
                    ErrorCodes.TOOL_INPUT_INVALID,
                    "level_place_features_batch: "
                            + features.size()
                            + " entries exceed the "
                            + MAX_ENTRIES
                            + "-entry cap; tile the scatter across calls");
        }

        // Pre-build the command strings on the HTTP thread (no world access here).
        record Placement(String feature, int x, int y, int z) {}
        java.util.List<Placement> placements = new java.util.ArrayList<>(features.size());
        int index = 0;
        for (JsonNode f : features) {
            // Validate each entry rather than dereferencing straight from get(). The declared
            // schema is not enforced per array element, so an entry shaped even slightly
            // differently -- {feature, position:{x,y,z}} instead of {feature, x, y, z} is the
            // easy mistake -- made get("x") return null and the .asInt() blew up as an
            // unhandled NullPointerException reported to the caller as "Internal server error",
            // which says nothing about which entry was wrong or why.
            if (f == null || !f.isObject()) {
                throw new McpException(
                        ErrorCodes.TOOL_INPUT_INVALID,
                        "level_place_features_batch: features[" + index + "] must be an object");
            }
            for (String key : new String[] {"feature", "x", "y", "z"}) {
                if (!f.hasNonNull(key)) {
                    throw new McpException(
                            ErrorCodes.TOOL_INPUT_INVALID,
                            "level_place_features_batch: features["
                                    + index
                                    + "] is missing '"
                                    + key
                                    + "'. Each entry is {feature, x, y, z} with flat integer"
                                    + " coordinates -- not a nested position object.");
                }
            }
            placements.add(
                    new Placement(
                            f.get("feature").asText(),
                            f.get("x").asInt(),
                            f.get("y").asInt(),
                            f.get("z").asInt()));
            index++;
        }

        return onMainThread(
                context,
                ignored -> {
                    int placed = 0;
                    int failed = 0;
                    ArrayNode errors = context.mapper().createArrayNode();
                    for (Placement p : placements) {
                        String cmd =
                                String.format(
                                        Locale.ROOT,
                                        "execute in %s run place feature %s %d %d %d",
                                        dim,
                                        p.feature(),
                                        p.x(),
                                        p.y(),
                                        p.z());
                        CommandResult res = context.adapter().commandExecute(cmd);
                        boolean ok = res.error() == null && res.successCount() > 0;
                        if (ok) {
                            placed++;
                        } else {
                            failed++;
                            if (errors.size() < 50) {
                                ObjectNode e = context.mapper().createObjectNode();
                                e.put("feature", p.feature());
                                e.put("x", p.x());
                                e.put("y", p.y());
                                e.put("z", p.z());
                                e.put(
                                        "error",
                                        res.error() != null ? res.error() : "successCount 0");
                                errors.add(e);
                            }
                            if (stopOnError) {
                                break;
                            }
                        }
                    }
                    ObjectNode payload = context.mapper().createObjectNode();
                    payload.put("requested", placements.size());
                    payload.put("placed", placed);
                    payload.put("failed", failed);
                    if (errors.size() > 0) {
                        payload.set("errors", errors);
                    }
                    return ToolResult.ofToon(payload);
                });
    }
}
