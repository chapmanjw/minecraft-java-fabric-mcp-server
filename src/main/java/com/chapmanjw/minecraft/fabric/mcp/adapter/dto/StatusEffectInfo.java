package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/** Status effect (potion effect) currently applied to a living entity. */
public record StatusEffectInfo(
        String id,
        int amplifier,
        int remainingDurationTicks,
        boolean ambient,
        boolean showParticles,
        boolean showIcon) {}
