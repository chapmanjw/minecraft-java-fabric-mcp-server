package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/**
 * Live state of one dimension: time, weather, difficulty, spawn point, default game
 * mode. Read-only snapshot; {@code level_set_time} and friends mutate the underlying
 * level directly.
 *
 * <p>{@code weather} is the weather the server has set. {@code rainLevel} and
 * {@code thunderLevel} (0.0 to 1.0) are what the sky is showing: they move by 0.01 per
 * tick towards the set weather, so right after a change they still describe the old one.
 */
public record LevelInfo(
        String dimensionId,
        long timeOfDay,
        long gameTime,
        String weather,
        int weatherRemainingTicks,
        float rainLevel,
        float thunderLevel,
        String difficulty,
        boolean difficultyLocked,
        String defaultGameMode,
        Vec3i spawnPoint,
        boolean hardcore) {}
