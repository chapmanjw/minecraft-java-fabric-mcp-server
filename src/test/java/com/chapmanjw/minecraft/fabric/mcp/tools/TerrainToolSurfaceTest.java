package com.chapmanjw.minecraft.fabric.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import com.chapmanjw.minecraft.fabric.mcp.compat.ReadOnlyHeuristic;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Tool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.block.BlockTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelPlaceFeaturesBatchTool;

import java.util.List;

/**
 * Schema-level smoke coverage for every WS-C (0.4.0) terrain tool.
 *
 * <p>Verifies that each class:
 * <ol>
 *   <li>Carries an {@link McpTool} annotation with a non-empty name and description.</li>
 *   <li>Can be instantiated via the no-arg constructor used by the runtime registrar.</li>
 *   <li>Publishes a non-null JSON Schema with {@code type: object} at the root.</li>
 *   <li>Spot-checks required fields and read-only classification.</li>
 * </ol>
 *
 * <p>The read-only flag assertions lock the R5 fix: the two query/read tools
 * ({@code block_erode_hydraulic_status} and {@code block_erode_hydraulic_result}) must
 * classify as read-only, while the two world-mutating tools
 * ({@code block_erode_region} and {@code block_erode_hydraulic_start}) must NOT.
 */
class TerrainToolSurfaceTest {

    /** All six WS-C tool classes that must be registered. */
    private static final List<Class<? extends Tool>> TOOL_CLASSES =
            List.of(
                    LevelPlaceFeaturesBatchTool.class,
                    BlockTools.FillColumnsStrata.class,
                    BlockTools.ErodeRegion.class,
                    BlockTools.HydraulicErodeStart.class,
                    BlockTools.HydraulicErodeStatus.class,
                    BlockTools.HydraulicErodeResult.class);

    @Test
    void everyTerrainToolHasAnnotation() {
        for (Class<? extends Tool> klass : TOOL_CLASSES) {
            McpTool meta = klass.getAnnotation(McpTool.class);
            assertNotNull(meta, klass.getName() + " is missing @McpTool");
            assertFalse(meta.name().isBlank(), klass.getName() + " has blank name");
            assertFalse(meta.description().isBlank(), klass.getName() + " has blank description");
        }
    }

    @Test
    void everyTerrainToolInstantiatesAndPublishesSchema() throws Exception {
        for (Class<? extends Tool> klass : TOOL_CLASSES) {
            Tool tool = klass.getDeclaredConstructor().newInstance();
            JsonNode schema = tool.inputSchema();
            assertNotNull(schema, klass.getName() + " published null schema");
            assertEquals(
                    "object",
                    schema.path("type").asText(),
                    klass.getName() + " schema root is not type=object");
        }
    }

    // -------------------------------------------------------------------
    // Spot-checks: required fields
    // -------------------------------------------------------------------

    @Test
    void batchFeaturesToolRequiresDimensionAndFeatures() {
        JsonNode schema = instantiate(LevelPlaceFeaturesBatchTool.class).inputSchema();
        JsonNode req = schema.path("required");
        assertTrue(req.isArray(), "required must be an array");
        assertTrue(containsText(req, "dimension"), "level_place_features_batch must require 'dimension'");
        assertTrue(containsText(req, "features"), "level_place_features_batch must require 'features'");
    }

    @Test
    void erodeRegionRequiresDimensionOriginWidthLengthFloorY() {
        JsonNode schema = instantiate(BlockTools.ErodeRegion.class).inputSchema();
        JsonNode req = schema.path("required");
        assertTrue(containsText(req, "dimension"), "block_erode_region must require 'dimension'");
        assertTrue(containsText(req, "origin"), "block_erode_region must require 'origin'");
        assertTrue(containsText(req, "width"), "block_erode_region must require 'width'");
        assertTrue(containsText(req, "length"), "block_erode_region must require 'length'");
        assertTrue(containsText(req, "floor_y"), "block_erode_region must require 'floor_y'");
    }

    @Test
    void hydraulicStartRequiresDimensionOriginWidthLengthFloorY() {
        JsonNode schema = instantiate(BlockTools.HydraulicErodeStart.class).inputSchema();
        JsonNode req = schema.path("required");
        assertTrue(containsText(req, "dimension"), "block_erode_hydraulic_start must require 'dimension'");
        assertTrue(containsText(req, "origin"), "block_erode_hydraulic_start must require 'origin'");
        assertTrue(containsText(req, "width"), "block_erode_hydraulic_start must require 'width'");
        assertTrue(containsText(req, "length"), "block_erode_hydraulic_start must require 'length'");
        assertTrue(containsText(req, "floor_y"), "block_erode_hydraulic_start must require 'floor_y'");
    }

    @Test
    void hydraulicStatusRequiresJobId() {
        JsonNode schema = instantiate(BlockTools.HydraulicErodeStatus.class).inputSchema();
        JsonNode req = schema.path("required");
        assertTrue(containsText(req, "job_id"), "block_erode_hydraulic_status must require 'job_id'");
    }

    @Test
    void hydraulicResultRequiresJobId() {
        JsonNode schema = instantiate(BlockTools.HydraulicErodeResult.class).inputSchema();
        JsonNode req = schema.path("required");
        assertTrue(containsText(req, "job_id"), "block_erode_hydraulic_result must require 'job_id'");
    }

    @Test
    void fillColumnsStrataRequiresDimensionOriginWidthLengthFloorYAndStrataFields() {
        JsonNode schema = instantiate(BlockTools.FillColumnsStrata.class).inputSchema();
        JsonNode req = schema.path("required");
        assertTrue(containsText(req, "dimension"), "block_fill_columns_strata must require 'dimension'");
        assertTrue(containsText(req, "origin"), "block_fill_columns_strata must require 'origin'");
        assertTrue(containsText(req, "width"), "block_fill_columns_strata must require 'width'");
        assertTrue(containsText(req, "length"), "block_fill_columns_strata must require 'length'");
        assertTrue(containsText(req, "floor_y"), "block_fill_columns_strata must require 'floor_y'");
        assertTrue(containsText(req, "strata"), "block_fill_columns_strata must require 'strata'");
        assertTrue(containsText(req, "base_stone"), "block_fill_columns_strata must require 'base_stone'");
    }

    // -------------------------------------------------------------------
    // Read-only classification: locks the R5 readonly fix
    // -------------------------------------------------------------------

    /**
     * block_erode_hydraulic_status — annotation sets readOnly=true AND name contains "_status"
     * (a ReadOnlyHeuristic fragment). Either path must classify it read-only.
     */
    @Test
    void hydraulicStatusIsReadOnly() {
        Class<BlockTools.HydraulicErodeStatus> klass = BlockTools.HydraulicErodeStatus.class;
        McpTool meta = klass.getAnnotation(McpTool.class);
        assertNotNull(meta);
        boolean annotationSaysReadOnly = meta.readOnly();
        boolean heuristicSaysReadOnly = ReadOnlyHeuristic.isReadOnly(meta.name());
        assertTrue(
                annotationSaysReadOnly || heuristicSaysReadOnly,
                "block_erode_hydraulic_status must be read-only via annotation OR heuristic; "
                        + "annotation=" + annotationSaysReadOnly
                        + " heuristic=" + heuristicSaysReadOnly);
    }

    /**
     * block_erode_hydraulic_result — annotation sets readOnly=true. The name does NOT
     * contain a heuristic fragment, so the annotation-override path is the active one.
     */
    @Test
    void hydraulicResultIsReadOnlyViaAnnotation() {
        Class<BlockTools.HydraulicErodeResult> klass = BlockTools.HydraulicErodeResult.class;
        McpTool meta = klass.getAnnotation(McpTool.class);
        assertNotNull(meta);
        assertTrue(
                meta.readOnly(),
                "block_erode_hydraulic_result must carry readOnly=true on @McpTool "
                        + "(name '" + meta.name() + "' has no heuristic read-verb fragment)");
    }

    /**
     * block_erode_region is a world-mutating tool — it must NOT be classified read-only
     * by either path.
     */
    @Test
    void erodeRegionIsNotReadOnly() {
        Class<BlockTools.ErodeRegion> klass = BlockTools.ErodeRegion.class;
        McpTool meta = klass.getAnnotation(McpTool.class);
        assertNotNull(meta);
        assertFalse(meta.readOnly(), "block_erode_region annotation must NOT set readOnly=true");
        assertFalse(
                ReadOnlyHeuristic.isReadOnly(meta.name()),
                "block_erode_region name must NOT match any read-only heuristic fragment");
    }

    /**
     * block_erode_hydraulic_start starts a world-mutating async job — must NOT be read-only.
     */
    @Test
    void hydraulicStartIsNotReadOnly() {
        Class<BlockTools.HydraulicErodeStart> klass = BlockTools.HydraulicErodeStart.class;
        McpTool meta = klass.getAnnotation(McpTool.class);
        assertNotNull(meta);
        assertFalse(meta.readOnly(), "block_erode_hydraulic_start annotation must NOT set readOnly=true");
        assertFalse(
                ReadOnlyHeuristic.isReadOnly(meta.name()),
                "block_erode_hydraulic_start name must NOT match any read-only heuristic fragment");
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private static Tool instantiate(Class<? extends Tool> klass) {
        try {
            return klass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(klass.getName(), e);
        }
    }

    private static boolean containsText(JsonNode array, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.asText())) {
                return true;
            }
        }
        return false;
    }
}
