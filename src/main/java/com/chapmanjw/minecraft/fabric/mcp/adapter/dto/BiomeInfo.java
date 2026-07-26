package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/**
 * What a biome is like, both numerically and visually.
 *
 * <p>The climate fields decide behaviour. {@code hasPrecipitation} is whether anything falls at
 * all; {@code temperature} then decides rain versus snow. {@code downfall} decides NEITHER --
 * vanilla reads it only as the second axis into the grass/foliage colour gradient (the only three
 * call sites are getGrassColorFromTexture, getFoliageColorFromTexture and
 * getDryFoliageColorFromTexture), which is why it sits next to the colours here rather than with
 * the weather fields.
 *
 * <p>{@code precipitation} is the resolved answer for one specific block: none, rain or snow. It is
 * position-dependent, because temperature falls off with altitude -- the same biome snows on a peak
 * and rains in the valley. It is null when the descriptor was built without a position (the
 * dimension listing), where no single answer exists.
 *
 * <p>The colours are packed 0xRRGGBB ints, the same values the client tints with, so a caller can
 * reason about how a biome will actually look. {@code grassColor} is likewise position-dependent
 * (swamp and dark forest apply a modifier) and null in the listing case.
 */
public record BiomeInfo(
        String id,
        float temperature,
        float downfall,
        boolean hasPrecipitation,
        String precipitation,
        Integer grassColor,
        int foliageColor,
        int dryFoliageColor,
        int waterColor,
        String grassColorModifier) {

    /** Mask that drops any alpha byte, leaving the 24 RGB bits. */
    private static final int RGB_MASK = 0xFFFFFF;

    /** Formats a packed 0xRRGGBB colour as {@code #RRGGBB}. */
    public static String hex(int rgb) {
        return String.format("#%06X", rgb & RGB_MASK);
    }
}
