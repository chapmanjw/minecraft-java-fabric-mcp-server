package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.List;
import java.util.UUID;

/**
 * Snapshot of a single named boss bar registered via {@code /bossbar add}.
 *
 * <p>Boss bars are server-wide entities owned by {@code MinecraftServer#getCustomBossEvents()};
 * the same instance is shown to every player whose UUID appears in {@link #players}.
 */
public record BossbarInfo(
        String id,
        String name,
        int value,
        int max,
        String color,
        String style,
        boolean visible,
        List<UUID> players) {

    public BossbarInfo {
        players = players == null ? List.of() : List.copyOf(players);
    }
}
