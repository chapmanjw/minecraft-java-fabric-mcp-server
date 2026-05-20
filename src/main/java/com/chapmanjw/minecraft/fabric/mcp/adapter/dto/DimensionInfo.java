package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/**
 * Static info about a server dimension.
 *
 * <p>The legacy {@code biomeSource} string was removed in v0.2.x — the adapter never
 * had a stable way to populate it across versions and it always returned an empty
 * string at runtime. Clients that need the biome source identifier should query
 * the biome registry directly via {@code level_list_biomes_in_dimension}.
 */
public record DimensionInfo(
        String id,
        String typeId,
        int minY,
        int maxY,
        long timeOfDay,
        boolean hasCeiling,
        boolean ultrawarm,
        boolean piglinSafe,
        boolean natural) {}
