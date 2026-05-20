package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/** Read view of an item stack — used in inventories, drops, and {@code itemstack_describe}. */
public record ItemStackInfo(
        String id, int count, String components, int maxStackSize, int maxDurability, int damage) {}
