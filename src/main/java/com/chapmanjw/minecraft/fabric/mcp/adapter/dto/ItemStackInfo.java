package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.List;

/**
 * Read view of an item stack — used in inventories, drops, and {@code itemstack_describe}.
 *
 * <p>The {@code componentKeys} field is the list of {@code DataComponentType} identifiers
 * present on the stack (e.g. {@code minecraft:max_stack_size}, {@code minecraft:enchantments}).
 * The values themselves are intentionally omitted: the runtime {@code Component.toString()}
 * leaks intermediary class names (e.g. {@code class_10711[...]}) and balloons the payload
 * to tens of kilobytes per stack. Callers that need component values should use
 * {@code entity_get_nbt} or {@code block_entity_get_nbt} to retrieve full SNBT.
 */
public record ItemStackInfo(
        String id,
        int count,
        List<String> componentKeys,
        int maxStackSize,
        int maxDurability,
        int damage) {

    public ItemStackInfo {
        componentKeys = componentKeys == null ? List.of() : List.copyOf(componentKeys);
    }
}
