package com.chapmanjw.minecraft.fabric.mcp.adapter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * DTO/record smoke coverage for the erosion-related value types added in WS-C (0.4.0).
 *
 * <p>Mirrors the {@code NewDtoSmokeTest} pattern — every record's constructor, accessors,
 * and notable invariants (null heights on apply-shaped ctor, populated heights on dry-run
 * ctor) are exercised without any Minecraft runtime.
 */
class ErosionRecordsSmokeTest {

    // -------------------------------------------------------------------
    // ErodeResult
    // -------------------------------------------------------------------

    /**
     * Apply-shaped construction: heights must be null because the applied form does not
     * return the height grid (it would bloat the response and the result is already in-world).
     */
    @Test
    void erodeResult_applyShape_heightsIsNull() {
        MinecraftAdapter.ErodeResult result =
                new MinecraftAdapter.ErodeResult(
                        100,    // columns
                        3456L,  // blocksChanged
                        5,      // maxDelta
                        1.2,    // meanAbsDelta
                        78.9,   // moved
                        8,      // iterations
                        null);  // heights — null for an apply run
        assertEquals(100, result.columns());
        assertEquals(3456L, result.blocksChanged());
        assertEquals(5, result.maxDelta());
        assertEquals(1.2, result.meanAbsDelta(), 1e-9);
        assertEquals(78.9, result.moved(), 1e-9);
        assertEquals(8, result.iterations());
        assertNull(result.heights(), "heights must be null for an apply (non-dry-run) result");
    }

    /**
     * Dry-run construction: the heights array is populated so the client can
     * render-verify the proposal. Round-trips intact through the record.
     */
    @Test
    void erodeResult_dryRunShape_heightsRoundTrips() {
        int[] heights = {64, 65, 63, 66, 64};
        MinecraftAdapter.ErodeResult result =
                new MinecraftAdapter.ErodeResult(
                        5,
                        0L,
                        3,
                        0.8,
                        12.5,
                        4,
                        heights);
        assertNotNull(result.heights(), "heights must be non-null for a dry-run result");
        assertArrayEquals(heights, result.heights(), "heights array must round-trip intact");
        assertEquals(5, result.columns());
        assertEquals(0L, result.blocksChanged());
    }

    // -------------------------------------------------------------------
    // ErodedApplyResult
    // -------------------------------------------------------------------

    @Test
    void erodedApplyResult_accessorsExact() {
        MinecraftAdapter.ErodedApplyResult res = new MinecraftAdapter.ErodedApplyResult(1234L, 512);
        assertEquals(1234L, res.blocksChanged());
        assertEquals(512, res.colsAdvanced());
    }

    /** Zero cols advanced signals the write-back cursor should not move (unloaded chunk). */
    @Test
    void erodedApplyResult_zeroColsAdvanced() {
        MinecraftAdapter.ErodedApplyResult stalled = new MinecraftAdapter.ErodedApplyResult(0L, 0);
        assertEquals(0L, stalled.blocksChanged());
        assertEquals(0, stalled.colsAdvanced());
    }

    // -------------------------------------------------------------------
    // ColumnStrataFill
    // -------------------------------------------------------------------

    @Test
    void columnStrataFill_constructorAndAccessors() {
        List<String> palette = List.of("minecraft:grass_block", "minecraft:dirt", "minecraft:water");
        int[] height = {64, 65, 64, 63};
        int[] surface = {0, 0, 0, 0};
        int[] subsurface = {1, 1, 1, 1};
        List<String> strataBlocks = List.of("minecraft:sandstone", "minecraft:red_sandstone");
        int[] strataThk = {8, 12};

        MinecraftAdapter.ColumnStrataFill spec =
                new MinecraftAdapter.ColumnStrataFill(
                        100,          // originX
                        200,          // originZ
                        2,            // width
                        2,            // length
                        -64,          // floorY
                        62,           // seaLevel
                        palette,      // palette
                        3,            // subsurfaceDepth
                        2,            // waterIndex
                        height,       // height
                        surface,      // surface
                        subsurface,   // subsurface
                        strataBlocks, // strataBlocks
                        strataThk,    // strataThickness
                        "minecraft:stone", // baseStone
                        4,            // jitterAmplitude
                        0.05);        // jitterFreq

        assertEquals(100, spec.originX());
        assertEquals(200, spec.originZ());
        assertEquals(2, spec.width());
        assertEquals(2, spec.length());
        assertEquals(-64, spec.floorY());
        assertEquals(62, spec.seaLevel());
        assertEquals(3, spec.palette().size());
        assertEquals("minecraft:grass_block", spec.palette().get(0));
        assertEquals(3, spec.subsurfaceDepth());
        assertEquals(2, spec.waterIndex());
        assertArrayEquals(height, spec.height());
        assertArrayEquals(surface, spec.surface());
        assertArrayEquals(subsurface, spec.subsurface());
        assertEquals(2, spec.strataBlocks().size());
        assertEquals("minecraft:sandstone", spec.strataBlocks().get(0));
        assertArrayEquals(strataThk, spec.strataThickness());
        assertEquals("minecraft:stone", spec.baseStone());
        assertEquals(4, spec.jitterAmplitude());
        assertEquals(0.05, spec.jitterFreq(), 1e-9);
    }

    // -------------------------------------------------------------------
    // ErodeSpec
    // -------------------------------------------------------------------

    @Test
    void erodeSpec_constructorAndAccessors() {
        MinecraftAdapter.ErodeSpec spec =
                new MinecraftAdapter.ErodeSpec(
                        -100,   // originX
                        -200,   // originZ
                        64,     // width
                        64,     // length
                        -60,    // floorY
                        8,      // iterations
                        1.0,    // talus
                        0.5,    // strength
                        "minecraft:grass_block",  // surface
                        "minecraft:dirt",          // subsurface
                        3,      // subsurfaceDepth
                        Integer.MIN_VALUE, // protectX0 (no protect box)
                        Integer.MIN_VALUE,
                        Integer.MIN_VALUE,
                        Integer.MIN_VALUE,
                        0,      // apron
                        true);  // dryRun

        assertEquals(-100, spec.originX());
        assertEquals(-200, spec.originZ());
        assertEquals(64, spec.width());
        assertEquals(64, spec.length());
        assertEquals(-60, spec.floorY());
        assertEquals(8, spec.iterations());
        assertEquals(1.0, spec.talus(), 1e-9);
        assertEquals(0.5, spec.strength(), 1e-9);
        assertEquals("minecraft:grass_block", spec.surface());
        assertEquals("minecraft:dirt", spec.subsurface());
        assertEquals(3, spec.subsurfaceDepth());
        assertEquals(Integer.MIN_VALUE, spec.protectX0());
        assertEquals(0, spec.apron());
        assertEquals(true, spec.dryRun());
    }

    /** Verify protectBox present (non-MIN_VALUE sentinel). */
    @Test
    void erodeSpec_withProtectBox() {
        MinecraftAdapter.ErodeSpec spec =
                new MinecraftAdapter.ErodeSpec(
                        0, 0, 32, 32, -60,
                        4, 1.0, 0.5,
                        "minecraft:grass_block", "minecraft:dirt", 3,
                        10, 10, 20, 20,   // protect box x0,z0,x1,z1
                        8,                // apron
                        false);
        assertEquals(10, spec.protectX0());
        assertEquals(10, spec.protectZ0());
        assertEquals(20, spec.protectX1());
        assertEquals(20, spec.protectZ1());
        assertEquals(8, spec.apron());
        org.junit.jupiter.api.Assertions.assertFalse(spec.dryRun());
    }
}
