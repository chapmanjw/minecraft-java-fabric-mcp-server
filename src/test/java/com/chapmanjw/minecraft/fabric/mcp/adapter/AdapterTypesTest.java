package com.chapmanjw.minecraft.fabric.mcp.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BlockStateInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;

import org.junit.jupiter.api.Test;

/**
 * Coverage for the adapter package's value types that don't depend on a running
 * Minecraft server — {@link AdapterException}, the nested {@code MinecraftAdapter}
 * enums/records, etc.
 */
class AdapterTypesTest {

    @Test
    void adapterExceptionCarriesMessage() {
        var ex = new AdapterException("oops");
        assertEquals("oops", ex.getMessage());
    }

    @Test
    void adapterExceptionCarriesMessageAndCause() {
        var cause = new IllegalStateException("inner");
        var ex = new AdapterException("outer", cause);
        assertEquals("outer", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void fillModeEnumValuesAreStable() {
        // The wire schema for block_fill_region depends on these names.
        var modes = MinecraftAdapter.FillMode.values();
        assertEquals(5, modes.length);
        assertEquals(MinecraftAdapter.FillMode.REPLACE, MinecraftAdapter.FillMode.valueOf("REPLACE"));
        assertEquals(MinecraftAdapter.FillMode.DESTROY, MinecraftAdapter.FillMode.valueOf("DESTROY"));
        assertEquals(MinecraftAdapter.FillMode.HOLLOW, MinecraftAdapter.FillMode.valueOf("HOLLOW"));
        assertEquals(MinecraftAdapter.FillMode.OUTLINE, MinecraftAdapter.FillMode.valueOf("OUTLINE"));
        assertEquals(MinecraftAdapter.FillMode.KEEP, MinecraftAdapter.FillMode.valueOf("KEEP"));
    }

    @Test
    void cloneModeEnumValuesAreStable() {
        var modes = MinecraftAdapter.CloneMode.values();
        assertEquals(3, modes.length);
        assertEquals(MinecraftAdapter.CloneMode.NORMAL, MinecraftAdapter.CloneMode.valueOf("NORMAL"));
        assertEquals(MinecraftAdapter.CloneMode.MASKED, MinecraftAdapter.CloneMode.valueOf("MASKED"));
        assertEquals(MinecraftAdapter.CloneMode.MOVE, MinecraftAdapter.CloneMode.valueOf("MOVE"));
    }

    @Test
    void blockMatchRecordCarriesPositionAndState() {
        var pos = new Vec3i(1, 2, 3);
        var state = new BlockStateInfo("minecraft:stone", null, 0, 1.5f, false, null);
        var match = new MinecraftAdapter.BlockMatch(pos, state);
        assertEquals(pos, match.position());
        assertSame(state, match.state());
        // Records auto-generate toString — just smoke-check it's non-null.
        assertNotNull(match.toString());
    }
}
