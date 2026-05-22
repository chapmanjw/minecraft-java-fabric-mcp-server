package com.chapmanjw.minecraft.fabric.mcp.adapter.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** Unit tests for the pure voxel-to-PNG renderer behind {@code block_render_region}. */
final class VoxelRendererTest {

    private static int[] solidCube(int n) {
        int[] colors = new int[n * n * n];
        Arrays.fill(colors, 0x33AA55);
        return colors;
    }

    private static void assertPng(byte[] png) {
        assertNotNull(png);
        assertTrue(png.length > 50, "PNG bytes suspiciously small: " + png.length);
        // PNG signature: 89 50('P') 4E('N') 47('G')
        assertTrue(
                (png[0] & 0xFF) == 0x89
                        && (png[1] & 0xFF) == 'P'
                        && (png[2] & 0xFF) == 'N'
                        && (png[3] & 0xFF) == 'G',
                "bytes are not a PNG (bad signature)");
    }

    @Test
    void rendersEachViewToPng() {
        int n = 4;
        int[] cube = solidCube(n);
        for (String view : new String[] {"iso", "side", "front", "top"}) {
            assertPng(VoxelRenderer.render(cube, n, n, n, view, 4));
        }
    }

    @Test
    void emptyGridStillProducesPng() {
        int[] empty = new int[2 * 2 * 2];
        assertPng(VoxelRenderer.render(empty, 2, 2, 2, "iso", 4));
    }

    @Test
    void unknownViewFallsBackToIso() {
        int n = 3;
        assertPng(VoxelRenderer.render(solidCube(n), n, n, n, "bogus-view", 4));
    }
}
