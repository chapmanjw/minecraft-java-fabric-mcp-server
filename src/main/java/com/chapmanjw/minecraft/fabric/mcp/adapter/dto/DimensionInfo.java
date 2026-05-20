package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/** Static info about a server dimension. */
public record DimensionInfo(
        String id,
        String typeId,
        int minY,
        int maxY,
        long timeOfDay,
        boolean hasCeiling,
        boolean ultrawarm,
        boolean piglinSafe,
        boolean natural,
        String biomeSource) {}
