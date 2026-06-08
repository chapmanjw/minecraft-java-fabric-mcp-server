package com.chapmanjw.minecraft.fabric.mcp;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chapmanjw.minecraft.fabric.mcp.adapter.client.ClientAccessImpl;
import com.chapmanjw.minecraft.fabric.mcp.compat.McEnvironment;
import com.chapmanjw.minecraft.fabric.mcp.compat.ToolCompatibilityFilter;
import com.chapmanjw.minecraft.fabric.mcp.config.Config;
import com.chapmanjw.minecraft.fabric.mcp.config.ConfigLoader;
import com.chapmanjw.minecraft.fabric.mcp.protocol.McpDispatcher;
import com.chapmanjw.minecraft.fabric.mcp.protocol.McpHttpRoute;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolRegistry;
import com.chapmanjw.minecraft.fabric.mcp.runtime.MinecraftMainThreadExecutor;
import com.chapmanjw.minecraft.fabric.mcp.tools.ClientToolRegistration;
import com.chapmanjw.minecraft.fabric.mcp.transport.HttpTransport;

/**
 * Client-side Fabric entry point: the {@code minecraft-java-client} MCP server.
 *
 * <p>This is the client cousin of {@link McpServerMod}. It runs inside a real, GPU-rendered
 * Minecraft client (single-player, or a client joined to a remote dedicated server) and exposes
 * the {@code client} category — the read-only inspection tools that capture the rendered frame and
 * read client-side perception. It reuses the same transport / protocol / config / compat layers as
 * the server mod; the only difference is the seam (a {@link ClientAccessImpl} instead of the world
 * adapter) and the thread it marshals onto ({@code Minecraft.getInstance()::execute} instead of
 * {@code MinecraftServer::execute}).
 *
 * <p>Deployment patterns:
 *
 * <ul>
 *   <li><b>Server-only</b> — a dedicated server runs only {@link McpServerMod}: the
 *       {@code minecraft-java} endpoint (world tools). No client, no inspection tools.
 *   <li><b>Client-only</b> — a client runs this. In single-player the integrated server also runs
 *       {@link McpServerMod}, so you get BOTH endpoints from one process: {@code minecraft-java}
 *       (world) on 8765 and {@code minecraft-java-client} (inspection) on 8766.
 *   <li><b>Server + client combo</b> — the dedicated server runs {@link McpServerMod}
 *       ({@code minecraft-java}); a separate client joins it over loopback and runs this
 *       ({@code minecraft-java-client}). The client endpoint is restricted to the {@code client}
 *       category by default (see {@link Config#clientDefaults()}), so it serves inspection only.
 * </ul>
 *
 * <p>The listener binds for the client's lifetime once the game has started; tools return a clean
 * "not in a world" error until the client joins a world.
 */
@Environment(EnvType.CLIENT)
public final class McpClientMod implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft_fabric_mcp/client");

    /** Client config lives next to the server config, under a distinct filename. */
    private static final String CLIENT_CONFIG_FILE = "client.json";

    /** Env prefix for client overrides — distinct from the server's {@code MCP_} to avoid clashes. */
    private static final String CLIENT_ENV_PREFIX = "MCP_CLIENT_";

    private MinecraftMainThreadExecutor executor;
    private HttpTransport transport;
    private Config loadedConfig;
    private long tickCount;

    private static final long PRUNE_RATE_LIMITS_EVERY_N_TICKS = 600L;
    private static final long RATE_LIMIT_IDLE_THRESHOLD_NANOS =
            java.util.concurrent.TimeUnit.MINUTES.toNanos(10);

    @Override
    public void onInitializeClient() {
        LOGGER.info("MCP client mod loading (id={})", McpServerMod.MOD_ID);
        ClientLifecycleEvents.CLIENT_STARTED.register(this::onClientStarted);
        ClientLifecycleEvents.CLIENT_STOPPING.register(this::onClientStopping);
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private void onClientStarted(Minecraft client) {
        try {
            Path configPath = clientConfigFilePath();
            Config config =
                    new ConfigLoader(CLIENT_ENV_PREFIX, Config.clientDefaults()).load(configPath);
            this.loadedConfig = config;
            LOGGER.info("MCP client config loaded from {}", configPath);

            McEnvironment env = McEnvironment.capture();
            ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env, config);
            ToolRegistry registry = ClientToolRegistration.buildRegistry(filter);
            LOGGER.info("Registered {} client inspection tool(s)", registry.size());

            // Reuse the generic main-thread executor, attached to the CLIENT thread.
            executor = new MinecraftMainThreadExecutor(config.commandTimeoutMs());
            executor.attach(client::execute);

            ObjectMapper mapper = new ObjectMapper();
            ClientAccessImpl clientAccess = new ClientAccessImpl(executor, mapper);
            ToolContext context =
                    new ToolContext(null, null, null, config, mapper, registry, null, clientAccess);

            McpDispatcher.ServerInfo info =
                    new McpDispatcher.ServerInfo(
                            "minecraft-java-fabric-mcp-client",
                            modVersion(),
                            "Inspect the Minecraft world as a player SEES it, from a real rendered client."
                                    + " view_capture returns a first-person PNG; sense_* and client_status"
                                    + " read client-side perception. Aim/position the player from the"
                                    + " minecraft-java (server) endpoint, then capture here.");
            McpDispatcher dispatcher = new McpDispatcher(registry, context, mapper, info);
            transport = new HttpTransport(config);
            transport.registerRoute("/mcp", new McpHttpRoute(dispatcher, mapper));
            transport.start();
            LOGGER.info(
                    "MCP client server listening on {} ({} inspection tools)",
                    config.endpointBase(),
                    registry.size());
        } catch (java.net.BindException be) {
            LOGGER.error(
                    "MCP client HTTP listener could not bind to {} — port already in use. Set a free"
                            + " port via `port` in {} or MCP_CLIENT_PORT.",
                    loadedConfig != null ? loadedConfig.endpointBase() : "the configured port",
                    CLIENT_CONFIG_FILE);
            LOGGER.debug("BindException detail", be);
            cleanup();
        } catch (IOException e) {
            LOGGER.error("MCP client HTTP listener failed to bind", e);
            cleanup();
        } catch (Exception e) {
            LOGGER.error("MCP client mod failed to start — the client will continue without it.", e);
            cleanup();
        }
    }

    private void onClientStopping(Minecraft client) {
        cleanup();
    }

    private void onEndTick(Minecraft client) {
        tickCount++;
        if (transport != null && tickCount % PRUNE_RATE_LIMITS_EVERY_N_TICKS == 0L) {
            transport.pruneIdleRateLimits(RATE_LIMIT_IDLE_THRESHOLD_NANOS);
        }
    }

    private void cleanup() {
        if (transport != null) {
            transport.stop();
            transport = null;
        }
        if (executor != null) {
            executor.detach();
            executor = null;
        }
    }

    private static Path clientConfigFilePath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(McpServerMod.MOD_ID)
                .resolve(CLIENT_CONFIG_FILE);
    }

    private static String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(McpServerMod.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }
}
