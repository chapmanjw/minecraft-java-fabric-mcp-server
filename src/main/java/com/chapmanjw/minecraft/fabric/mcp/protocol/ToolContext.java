package com.chapmanjw.minecraft.fabric.mcp.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.chapmanjw.minecraft.fabric.mcp.adapter.ClientAccess;
import com.chapmanjw.minecraft.fabric.mcp.adapter.MinecraftAdapter;
import com.chapmanjw.minecraft.fabric.mcp.config.Config;
import com.chapmanjw.minecraft.fabric.mcp.runtime.MinecraftMainThreadExecutor;
import com.chapmanjw.minecraft.fabric.mcp.runtime.AsyncJobRegistry;

/**
 * Per-server context handed to every {@link Tool#execute} call.
 *
 * <p>Holds references to long-lived collaborators. Tools should read from here rather
 * than reaching for static singletons — the context is what makes the tool layer
 * unit-testable without a running Minecraft server.
 *
 * <p>The server MCP server ({@code McpServerMod}) populates {@code adapter} /
 * {@code mainThreadExecutor} / {@code eventBus} / {@code jobs} and leaves {@code client} null.
 * The client MCP server ({@code McpClientMod}) populates {@code client} ({@link ClientAccess})
 * and leaves the server-side collaborators null — its registry only holds client tools, which
 * touch {@code client()} and never the server adapter. {@code ClientAccess} carries no
 * {@code net.minecraft.client.*} types, so this record stays loadable on a dedicated server.
 */
public record ToolContext(
        MinecraftAdapter adapter,
        MinecraftMainThreadExecutor mainThreadExecutor,
        EventBus eventBus,
        Config config,
        ObjectMapper mapper,
        ToolRegistry registry,
        AsyncJobRegistry jobs,
        ClientAccess client) {

    /**
     * Server-side convenience constructor (no client seam). Keeps {@code McpServerMod} and the
     * existing tool tests unchanged after the {@code client} field was added.
     */
    public ToolContext(
            MinecraftAdapter adapter,
            MinecraftMainThreadExecutor mainThreadExecutor,
            EventBus eventBus,
            Config config,
            ObjectMapper mapper,
            ToolRegistry registry,
            AsyncJobRegistry jobs) {
        this(adapter, mainThreadExecutor, eventBus, config, mapper, registry, jobs, null);
    }

    /** Convenience: re-export the main-thread default timeout for tool handlers. */
    public long defaultTimeoutMs() {
        return config.commandTimeoutMs();
    }
}
