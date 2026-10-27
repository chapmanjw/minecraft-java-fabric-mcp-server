package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.Map;

/**
 * What using a block-transforming tool (axe, hoe, shovel, or any item carrying a
 * {@code block_transformer} component) on a block would produce. Computed without changing the
 * world.
 *
 * @param block the block currently at the position
 * @param tool the item id that was asked about
 * @param transformer the {@code block_transformer} registry id the item carries
 * @param matched whether any transform applies to this block from this face
 * @param faceBlocked true when a transform was skipped only because the clicked face is one it
 *     disallows (the shovel refuses the underside, for example)
 * @param resultBlock the block it would become, or {@code null} when nothing matched
 * @param resultProperties block-state properties of the result
 * @param sound the sound event id vanilla plays, or empty
 * @param transformType {@code single_block} or {@code copper_chest}
 * @param consumesItem whether one of the item is used up; only stackable items are (wax, for
 *     example), and only when the transform asks for it
 * @param itemDamage durability a non-stackable tool loses; 0 for stackable items
 * @param lootTable loot table dropped by the transform (for example honeycomb), or empty
 */
public record BlockTransformPreview(
        String block,
        Map<String, String> blockProperties,
        String tool,
        String transformer,
        boolean matched,
        boolean faceBlocked,
        String resultBlock,
        Map<String, String> resultProperties,
        String sound,
        String transformType,
        boolean consumesItem,
        int itemDamage,
        String lootTable) {

    public BlockTransformPreview {
        blockProperties = blockProperties == null ? Map.of() : Map.copyOf(blockProperties);
        resultProperties = resultProperties == null ? Map.of() : Map.copyOf(resultProperties);
    }
}
