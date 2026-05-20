package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/** A vanilla game rule — name, current value (always stringified), and value type. */
public record GameRuleInfo(String name, String value, String type) {}
