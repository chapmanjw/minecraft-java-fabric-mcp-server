package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/** A pending entry in the {@code MinecraftServer#getFunctions()} scheduler. */
public record ScheduledFunctionInfo(String functionId, long ticksRemaining) {}
