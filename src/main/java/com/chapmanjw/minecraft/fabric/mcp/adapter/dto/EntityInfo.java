package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.List;
import java.util.UUID;

/**
 * Snapshot of a non-player entity. Fields are intentionally lossy compared to the
 * full entity NBT — use {@code entity_get_nbt} when full fidelity is required.
 */
public record EntityInfo(
        UUID uuid,
        String type,
        String customName,
        String dimensionId,
        Vec3d position,
        Vec3d velocity,
        float yaw,
        float pitch,
        float health,
        float maxHealth,
        boolean onGround,
        boolean alive,
        List<String> tags) {

    public EntityInfo {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
