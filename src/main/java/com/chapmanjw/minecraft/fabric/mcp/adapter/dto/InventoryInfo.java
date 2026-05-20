package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.List;

/**
 * Snapshot of a container's contents — generic enough to represent a player's
 * inventory, a chest, a hopper, or an entity's equipment slot map.
 *
 * <p>Empty slots are represented by an {@link ItemStackInfo} with {@code id="minecraft:air"}.
 */
public record InventoryInfo(int size, List<ItemStackInfo> slots) {

    public InventoryInfo {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }
}
