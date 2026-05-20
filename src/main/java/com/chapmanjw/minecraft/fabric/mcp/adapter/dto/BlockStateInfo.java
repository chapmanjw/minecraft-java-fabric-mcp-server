package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.Map;

/**
 * Read snapshot of a block at a given position. Tools serialize this directly to
 * JSON — the structure is the public output shape.
 */
public record BlockStateInfo(
        String id,
        Map<String, String> properties,
        int lightLevel,
        float hardness,
        boolean hasBlockEntity,
        String blockEntityNbt) {

    public BlockStateInfo {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
