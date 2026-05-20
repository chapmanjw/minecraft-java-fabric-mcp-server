package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.List;

/** A registered scoreboard team along with its current members. */
public record TeamInfo(
        String name,
        String displayName,
        String color,
        boolean friendlyFire,
        boolean seeInvisibles,
        List<String> members) {

    public TeamInfo {
        members = members == null ? List.of() : List.copyOf(members);
    }
}
