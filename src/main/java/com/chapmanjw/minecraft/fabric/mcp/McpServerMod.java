package com.chapmanjw.minecraft.fabric.mcp;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chapmanjw.minecraft.fabric.mcp.adapter.MinecraftAdapter;
import com.chapmanjw.minecraft.fabric.mcp.adapter.impl.MinecraftAdapterImpl;
import com.chapmanjw.minecraft.fabric.mcp.compat.McEnvironment;
import com.chapmanjw.minecraft.fabric.mcp.compat.ToolCompatibilityFilter;
import com.chapmanjw.minecraft.fabric.mcp.config.Config;
import com.chapmanjw.minecraft.fabric.mcp.config.ConfigLoader;
import com.chapmanjw.minecraft.fabric.mcp.protocol.EventBus;
import com.chapmanjw.minecraft.fabric.mcp.protocol.McpDispatcher;
import com.chapmanjw.minecraft.fabric.mcp.protocol.McpHttpRoute;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolRegistry;
import com.chapmanjw.minecraft.fabric.mcp.runtime.MinecraftMainThreadExecutor;
import com.chapmanjw.minecraft.fabric.mcp.tools.ToolRegistration;
import com.chapmanjw.minecraft.fabric.mcp.tools.events.EventWiring;
import com.chapmanjw.minecraft.fabric.mcp.transport.HttpTransport;

/**
 * Fabric mod entry point.
 *
 * <p>Wires the four layers together at server-starting and tears them down at
 * server-stopping. The HTTP listener binds only while a world is loaded; on a
 * dedicated server that's the entire lifetime, while in single-player it follows
 * the world load/unload cycle.
 */
public final class McpServerMod implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft_fabric_mcp");

    /** Mod version is templated into fabric.mod.json from gradle.properties. */
    public static final String MOD_ID = "minecraft_fabric_mcp";

    private MinecraftMainThreadExecutor executor;
    private MinecraftAdapter adapter;
    private HttpTransport transport;
    /** Most recently loaded config, retained so the bind-failure path can quote host/port. */
    private Config loadedConfig;

    /** Tick counter for cadence-aligned bookkeeping (rate-limiter prune, etc.). */
    private long tickCount;

    /** How often to prune idle rate-limit buckets. 600 ticks ≈ 30 s at 20 TPS. */
    private static final long PRUNE_RATE_LIMITS_EVERY_N_TICKS = 600L;

    /** Drop rate-limit buckets that have sat at full capacity longer than this. */
    private static final long RATE_LIMIT_IDLE_THRESHOLD_NANOS =
            java.util.concurrent.TimeUnit.MINUTES.toNanos(10);

    @Override
    public void onInitialize() {
        LOGGER.info("MCP server mod loading (id={})", MOD_ID);

        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(this::onServerStopped);
        ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);
    }

    private void onServerStarting(MinecraftServer server) {
        try {
            // -----------------------------------------------------------
            // 1) Config
            // -----------------------------------------------------------
            Path configPath = configFilePath();
            Config config = new ConfigLoader().load(configPath);
            this.loadedConfig = config;
            LOGGER.info("MCP server config loaded from {}", configPath);

            // -----------------------------------------------------------
            // 2) Environment + compat filter + tool registry
            // -----------------------------------------------------------
            McEnvironment env = McEnvironment.capture();
            LOGGER.info(
                    "Running on Minecraft {} (Fabric Loader {})",
                    env.minecraftVersion(),
                    env.fabricLoaderVersion());
            ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env, config);
            ToolRegistry registry = ToolRegistration.buildRegistry(filter);
            LOGGER.info(
                    "Registered {} tool(s) after applying compatibility + category filters",
                    registry.size());

            // -----------------------------------------------------------
            // 3) Runtime
            // -----------------------------------------------------------
            executor = new MinecraftMainThreadExecutor(config.commandTimeoutMs());
            executor.attach(server::execute);
            adapter = new MinecraftAdapterImpl(modVersion());
            adapter.bind(server);
            EventBus eventBus = new EventBus(config.eventBufferSize());

            // -----------------------------------------------------------
            // 4) Protocol + transport
            // -----------------------------------------------------------
            ObjectMapper mapper = new ObjectMapper();
            ToolContext context = new ToolContext(adapter, executor, eventBus, config, mapper, registry);
            McpDispatcher.ServerInfo info =
                    new McpDispatcher.ServerInfo(
                            "minecraft-java-fabric-mcp-server",
                            modVersion(),
                            "Operate the Minecraft world via MCP tools. Use server_get_status to introspect"
                                    + " the server. See docs/tools.md for the full tool surface.");
            McpDispatcher dispatcher = new McpDispatcher(registry, context, mapper, info);
            transport = new HttpTransport(config);
            transport.registerRoute("/mcp", new McpHttpRoute(dispatcher, mapper));

            // -----------------------------------------------------------
            // 5) Event wiring
            // -----------------------------------------------------------
            EventWiring.install(eventBus, mapper);

            LOGGER.info(
                    "MCP server pre-start complete: {} tools registered, listening on {}",
                    registry.size(),
                    config.endpointBase());
        } catch (Exception e) {
            LOGGER.error(
                    "MCP server failed to start — the Minecraft server will continue without it.", e);
            cleanupFailedStart();
        }
    }

    private void onServerStarted(MinecraftServer server) {
        if (transport == null) {
            return;
        }
        try {
            transport.start();
        } catch (java.net.BindException be) {
            // BindException is by far the most common boot failure ("port in use") —
            // give the operator a one-line actionable hint instead of a raw stack trace.
            // We still log the exception at DEBUG for genuine diagnosis.
            LOGGER.error(
                    "MCP HTTP listener could not bind to {}:{} — port already in use. "
                            + "Pick a free port via `port` in config.json or MCP_PORT, "
                            + "then restart the world.",
                    config().host(),
                    config().port());
            LOGGER.debug("BindException detail", be);
            transport = null;
        } catch (IOException e) {
            LOGGER.error("MCP HTTP listener failed to bind", e);
            transport = null;
        }
    }

    private Config config() {
        return loadedConfig;
    }

    private void onServerStopping(MinecraftServer server) {
        if (transport != null) {
            transport.stop();
        }
    }

    private void onServerStopped(MinecraftServer server) {
        if (adapter != null) {
            adapter.unbind();
        }
        if (executor != null) {
            executor.detach();
        }
        adapter = null;
        executor = null;
        transport = null;
    }

    private void onEndTick(MinecraftServer server) {
        // Periodic rate-limiter prune. Buckets accumulate per unique client key; on a
        // long-running shared server with many MCP clients this map would otherwise
        // grow without bound. Pruning is cheap (single ConcurrentHashMap iteration)
        // and happens off the hot path of request handling.
        tickCount++;
        if (transport != null && tickCount % PRUNE_RATE_LIMITS_EVERY_N_TICKS == 0L) {
            transport.pruneIdleRateLimits(RATE_LIMIT_IDLE_THRESHOLD_NANOS);
        }
    }

    private void cleanupFailedStart() {
        if (transport != null) {
            transport.stop();
            transport = null;
        }
        if (adapter != null) {
            adapter.unbind();
            adapter = null;
        }
        if (executor != null) {
            executor.detach();
            executor = null;
        }
    }

    private static Path configFilePath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(MOD_ID)
                .resolve("config.json");
    }

    private static String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }
}
