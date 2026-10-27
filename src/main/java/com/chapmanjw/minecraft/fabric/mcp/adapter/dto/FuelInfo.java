package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/**
 * How an item behaves as fuel.
 *
 * @param burnTimeTicks burn time in a regular furnace; 0 when the item is not cooking fuel
 * @param burnTimeTicksFast burn time in a blast furnace or smoker, which burn fuel twice as fast
 * @param speedMultiplier how much the fuel speeds up cooking in a regular furnace. Always 1.0
 *     before Minecraft 26.3, which introduced per-fuel speed
 * @param brewingFuelUses brews one item of brewing-stand fuel powers; 0 when it is not brewing fuel
 */
public record FuelInfo(
        int burnTimeTicks, int burnTimeTicksFast, float speedMultiplier, int brewingFuelUses) {}
