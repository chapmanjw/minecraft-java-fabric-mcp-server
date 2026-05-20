package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/**
 * A single fluid tank's contents reported by a Fabric {@code FluidStorage}.
 *
 * <p>Amounts and capacities are expressed in droplets (the unit used throughout
 * the Fabric transfer API). 81000 droplets equals one Minecraft bucket.
 *
 * <p>{@link #empty} marks tanks that report no fluid; {@link #fluidId} is the
 * Minecraft identifier for the fluid variant, e.g. {@code minecraft:water}.
 */
public record FluidStackInfo(
        boolean empty,
        String fluidId,
        long amountDroplets,
        long capacityDroplets) {

    public static FluidStackInfo emptyTank() {
        return new FluidStackInfo(true, "minecraft:empty", 0L, 0L);
    }
}
