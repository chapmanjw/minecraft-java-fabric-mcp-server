package com.chapmanjw.minecraft.fabric.mcp.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.chapmanjw.minecraft.fabric.mcp.adapter.MinecraftAdapter;
import com.chapmanjw.minecraft.fabric.mcp.config.Config;
import com.chapmanjw.minecraft.fabric.mcp.runtime.MinecraftMainThreadExecutor;

/**
 * Per-server context handed to every {@link Tool#execute} call.
 *
 * <p>Holds references to long-lived collaborators. Tools should read from here rather
 * than reaching for static singletons — the context is what makes the tool layer
 * unit-testable without a running Minecraft server.
 */
public record ToolContext(
        MinecraftAdapter adapter,
        MinecraftMainThreadExecutor mainThreadExecutor,
        EventBus eventBus,
        Config config,
        ObjectMapper mapper,
        ToolRegistry registry) {

    /** Convenience: re-export the main-thread default timeout for tool handlers. */
    public long defaultTimeoutMs() {
        return config.commandTimeoutMs();
    }
}
