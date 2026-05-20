package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/**
 * Composter level-up chance for an item, as registered with Fabric's
 * {@code CompostingChanceRegistry} (1.21.x) / {@code CompostableRegistry} (26.1+).
 */
public record CompostableInfo(boolean compostable, float chance) {

    public static CompostableInfo notCompostable() {
        return new CompostableInfo(false, 0.0f);
    }
}
