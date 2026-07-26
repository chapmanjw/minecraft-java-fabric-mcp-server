package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/**
 * What a biome is like, both numerically and visually.
 *
 * <p>The climate fields decide behaviour. {@code hasPrecipitation} is whether anything falls at
 * all; {@code temperature} then decides rain versus snow. {@code downfall} decides NEITHER --
 * vanilla reads it only as the second axis into the grass/foliage colour gradient (its only three
 * call sites are getGrassColorFromTexture, getFoliageColorFromTexture and
 * getDryFoliageColorFromTexture), which is why it sits with the colour fields here rather than the
 * weather ones.
 *
 * <p>{@code precipitation} is the resolved answer for one specific block: none, rain or snow. It is
 * position-dependent, because temperature falls off with altitude -- the same biome snows on a peak
 * and rains in the valley. It is null when the descriptor was built without a position (the
 * dimension listing), where no single answer exists.
 *
 * <p>The colour fields are deliberately limited to what a DEDICATED SERVER can actually know.
 * Biome.getGrassColor / getFoliageColor / getDryFoliageColor resolve through the client's
 * grass.png and foliage.png colourmaps, which a headless server never loads, so calling them
 * server-side yields 0x000000 for every biome -- verified live on 26.2, where deep_ocean returned
 * #000000 for all three. Reporting those would be the same lie as the hardcoded downfall this
 * class used to carry, so they are not exposed.
 *
 * <p>What IS real server-side: {@code waterColor}, which is a plain value on BiomeSpecialEffects
 * rather than a texture lookup; the three explicit overrides, present only for biomes that pin a
 * colour instead of sampling the gradient (swamp, dark forest, badlands and friends), and null
 * otherwise; and {@code grassColorModifier}, the post-processing vanilla applies on top. Together
 * with temperature and downfall those let a caller derive the gradient colour itself if it has the
 * colourmaps, without this class inventing a number it cannot compute.
 */
public record BiomeInfo(
        String id,
        float temperature,
        float downfall,
        boolean hasPrecipitation,
        String precipitation,
        int waterColor,
        Integer grassColorOverride,
        Integer foliageColorOverride,
        Integer dryFoliageColorOverride,
        String grassColorModifier) {

    /** Mask that drops any alpha byte, leaving the 24 RGB bits. */
    private static final int RGB_MASK = 0xFFFFFF;

    /** Formats a packed 0xRRGGBB colour as {@code #RRGGBB}. */
    public static String hex(int rgb) {
        return String.format("#%06X", rgb & RGB_MASK);
    }
}
