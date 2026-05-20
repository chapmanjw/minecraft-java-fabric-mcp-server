package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/** A registered scoreboard objective. */
public record ScoreboardObjectiveInfo(
        String name, String displayName, String criterion, String displaySlot) {}
