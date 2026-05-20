package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/** Metadata about a saved structure template. */
public record StructureInfo(
        String name,
        int sizeX,
        int sizeY,
        int sizeZ,
        long fileSizeBytes,
        boolean onDisk,
        boolean inMemory) {}
