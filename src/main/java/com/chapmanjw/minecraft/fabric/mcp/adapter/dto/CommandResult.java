package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.List;

/**
 * Outcome of executing a slash command. {@link #successCount} is the value returned
 * by the Brigadier dispatcher (vanilla semantics — 1 on success, &gt;1 for selectors
 * targeting multiple entities, 0 on failure). {@link #output} captures any messages
 * the command source produced.
 */
public record CommandResult(int successCount, List<String> output, String error) {

    public CommandResult {
        output = output == null ? List.of() : List.copyOf(output);
    }
}
