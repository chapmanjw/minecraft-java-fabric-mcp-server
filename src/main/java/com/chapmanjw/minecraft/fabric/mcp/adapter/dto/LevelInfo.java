package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/**
 * Live state of one dimension: time, weather, difficulty, spawn point, default game
 * mode. Read-only snapshot; {@code level_set_time} and friends mutate the underlying
 * level directly.
 */
public record LevelInfo(
        String dimensionId,
        long timeOfDay,
        long gameTime,
        String weather,
        int weatherRemainingTicks,
        String difficulty,
        boolean difficultyLocked,
        String defaultGameMode,
        Vec3i spawnPoint,
        boolean hardcore) {}
