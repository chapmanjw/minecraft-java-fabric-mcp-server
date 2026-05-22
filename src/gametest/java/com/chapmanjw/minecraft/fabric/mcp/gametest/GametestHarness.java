package com.chapmanjw.minecraft.fabric.mcp.gametest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.concurrent.atomic.AtomicReference;

import com.chapmanjw.minecraft.fabric.mcp.adapter.MinecraftAdapter;
import com.chapmanjw.minecraft.fabric.mcp.adapter.impl.MinecraftAdapterImpl;
import com.chapmanjw.minecraft.fabric.mcp.compat.McEnvironment;
import com.chapmanjw.minecraft.fabric.mcp.compat.ToolCompatibilityFilter;
import com.chapmanjw.minecraft.fabric.mcp.config.Config;
import com.chapmanjw.minecraft.fabric.mcp.protocol.EventBus;
import com.chapmanjw.minecraft.fabric.mcp.protocol.McpDispatcher;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolRegistry;
import com.chapmanjw.minecraft.fabric.mcp.runtime.MinecraftMainThreadExecutor;
import com.chapmanjw.minecraft.fabric.mcp.tools.ToolRegistration;

/**
 * Shared bootstrap helpers for {@code @GameTest} scenarios.
 *
 * <p>Each gametest method receives a {@code GameTestHelper}; from there it can spawn
 * entities, place blocks, and query the world. We piggyback on the same
 * {@link MinecraftAdapter} / {@link ToolContext} the production mod uses, so the
 * tests exercise the actual call path (HTTP dispatch is skipped — we invoke the
 * dispatcher directly with synthetic JSON-RPC requests).
 *
 * <p>The harness is intentionally minimal — gametest worlds are tiny structures
 * loaded from {@code data/minecraft_fabric_mcp/gametest/structure/empty.snbt}; setup helpers
 * here let each test grab a working {@link McpDispatcher} without copy-pasting the
 * wiring.
 */
public final class GametestHarness {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GametestHarness() {}

    /** Build a fully wired dispatcher around a running {@code MinecraftServer}. */
    public static Bootstrap bootstrap(Object minecraftServer) {
        MinecraftAdapter adapter = new MinecraftAdapterImpl("gametest");
        adapter.bind(minecraftServer);

        MinecraftMainThreadExecutor exec = new MinecraftMainThreadExecutor(15_000L);
        // For gametests we run synchronously on the calling thread — gametests already
        // run on the main thread by Fabric's harness.
        exec.attach(Runnable::run);

        EventBus eventBus = new EventBus(1024);
        Config config = Config.defaults();

        McEnvironment env = McEnvironment.capture();
        ToolRegistry registry = ToolRegistration.buildRegistry(new ToolCompatibilityFilter(env));

        ToolContext ctx = new ToolContext(adapter, exec, eventBus, config, MAPPER, registry);

        McpDispatcher dispatcher =
                new McpDispatcher(
                        registry,
                        ctx,
                        MAPPER,
                        new McpDispatcher.ServerInfo("gametest-mcp", "test", null));

        return new Bootstrap(registry, dispatcher, ctx, MAPPER);
    }

    /** Issue a JSON-RPC tools/call request and return the structured content payload. */
    public static JsonNode callTool(McpDispatcher dispatcher, String name, ObjectNode args) {
        ObjectNode req = MAPPER.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", 1);
        req.put("method", "tools/call");
        ObjectNode params = req.putObject("params");
        params.put("name", name);
        params.set("arguments", args == null ? MAPPER.createObjectNode() : args);
        JsonNode resp = dispatcher.handle(req);
        if (resp.has("error")) {
            throw new AssertionError("Tool '" + name + "' returned error: " + resp.path("error"));
        }
        return resp.path("result");
    }

    /** Container for the full set of wired objects a gametest may need. */
    public record Bootstrap(
            ToolRegistry registry,
            McpDispatcher dispatcher,
            ToolContext context,
            ObjectMapper mapper) {

        /** Number of registered tools (sanity check). */
        public int toolCount() {
            return registry.size();
        }
    }

    /**
     * Async helper for tests that simulate a slow Minecraft tick — pass to
     * {@link MinecraftMainThreadExecutor#attach} to defer execution by one tick.
     */
    public static AtomicReference<Runnable> pendingWork() {
        return new AtomicReference<>();
    }
}
