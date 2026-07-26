package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.List;

/**
 * Server-wide status snapshot. Read by {@code server_get_status} and by the protocol
 * layer when reporting health/uptime.
 */
public record ServerStatus(
        String minecraftVersion,
        String fabricLoaderVersion,
        String modVersion,
        String motd,
        long uptimeMillis,
        double averageTps,
        double averageMspt,
        int onlinePlayerCount,
        int maxPlayers,
        List<String> loadedDimensions,
        int registeredToolCount,
        int dataVersion,
        int datapackFormat,
        int resourcePackFormat) {

    public ServerStatus {
        loadedDimensions = loadedDimensions == null ? List.of() : List.copyOf(loadedDimensions);
    }
}
