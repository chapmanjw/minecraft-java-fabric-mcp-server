package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.Map;

/**
 * Spec for placing a block. {@link #id} is the registry-prefixed block name (e.g.
 * {@code "minecraft:oak_log"}). {@link #properties} maps blockstate property names
 * to their string-encoded values, matching the vanilla {@code /setblock} syntax.
 * {@link #nbt} is an optional SNBT string for block entity data.
 */
public record BlockSpec(String id, Map<String, String> properties, String nbt) {

    public BlockSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("BlockSpec.id must be non-blank");
        }
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public static BlockSpec of(String id) {
        return new BlockSpec(id, Map.of(), null);
    }
}
