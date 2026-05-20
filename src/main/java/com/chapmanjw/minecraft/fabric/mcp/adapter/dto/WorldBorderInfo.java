package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/**
 * Snapshot of a dimension's world border settings.
 *
 * <p>{@link #lerpTarget} and {@link #lerpTimeRemainingTicks} are {@code -1} when no
 * size-change interpolation is active. The border is always centred at one point in
 * the XZ plane and has identical extents in all four cardinal directions.
 */
public record WorldBorderInfo(
        double centerX,
        double centerZ,
        double size,
        int warningBlocks,
        int warningSeconds,
        double damagePerBlock,
        double safeZone,
        double lerpTarget,
        long lerpTimeRemainingTicks) {}
