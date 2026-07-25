package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.Map;

/**
 * The shape family a block belongs to: its base block and every shape variant vanilla defines for
 * it (stairs, slab, wall, fence, chiseled, cracked, and so on).
 *
 * @param base registry id of the family's base block, e.g. {@code minecraft:oak_planks}
 * @param matchedVariant the variant name the queried block corresponds to, or {@code "base"} when
 *     the query was the base block itself
 * @param variants variant name (lowercase, e.g. {@code "stairs"}) to that variant's registry id.
 *     Only variants vanilla actually defines for this family are present — that absence is the
 *     useful part, because it is how a caller learns a shape does not exist.
 */
public record BlockFamilyInfo(String base, String matchedVariant, Map<String, String> variants) {}
