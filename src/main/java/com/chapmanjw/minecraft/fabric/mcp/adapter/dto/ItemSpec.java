package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/**
 * Spec for an item stack. {@link #id} is registry-prefixed (e.g.
 * {@code "minecraft:diamond_pickaxe"}). {@link #count} clamps to {@code [1, 64]} at
 * the adapter boundary. {@link #components} carries the modern component map as a
 * compact SNBT-style string; pass {@code null} for "no components".
 *
 * <p>NBT-style legacy fields are accepted via {@link #components} for compatibility
 * with tools that still build SNBT strings — the adapter handles the conversion.
 */
public record ItemSpec(String id, int count, String components) {

    public ItemSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ItemSpec.id must be non-blank");
        }
        if (count < 1) {
            count = 1;
        }
    }

    public static ItemSpec of(String id) {
        return new ItemSpec(id, 1, null);
    }

    public static ItemSpec of(String id, int count) {
        return new ItemSpec(id, count, null);
    }
}
