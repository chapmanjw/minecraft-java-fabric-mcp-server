package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.List;

/** Generic recipe descriptor. */
public record RecipeInfo(
        String id,
        String type,
        String group,
        List<String> ingredients,
        String result,
        int resultCount) {

    public RecipeInfo {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
    }
}
