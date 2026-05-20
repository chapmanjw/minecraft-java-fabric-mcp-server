package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.UUID;

/** Live snapshot of a connected player. */
public record PlayerInfo(
        UUID uuid,
        String name,
        String dimensionId,
        Vec3d position,
        float yaw,
        float pitch,
        String gameMode,
        float health,
        float maxHealth,
        int foodLevel,
        float saturation,
        int xpLevel,
        float xpProgress,
        long latencyMs) {}
