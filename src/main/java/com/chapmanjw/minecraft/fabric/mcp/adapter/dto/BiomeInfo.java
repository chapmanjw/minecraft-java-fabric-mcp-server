package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/** Lightweight biome descriptor. */
public record BiomeInfo(
        String id, float temperature, float downfall, boolean hasPrecipitation) {}
