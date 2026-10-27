package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/**
 * One entry of the natural-spawn list the game uses at a position.
 *
 * @param category mob category, e.g. {@code monster}, {@code creature}, {@code water_creature}
 * @param entityType entity type id
 * @param weight relative weight within its category
 * @param minCount smallest group size
 * @param maxCount largest group size
 */
public record MobSpawnEntry(String category, String entityType, int weight, int minCount, int maxCount) {}
