package com.chapmanjw.minecraft.fabric.mcp.config;

import java.util.List;
import java.util.Objects;

/**
 * Immutable configuration for the MCP server.
 *
 * <p>Created by {@link ConfigLoader} after applying file values, environment variable
 * overrides, validation, and (if {@code authRequired} and no token is supplied) bearer
 * token generation. Treat instances as authoritative — every layer downstream reads
 * from this record without re-resolving environment variables.
 *
 * <p>The {@code includedCategories} / {@code excludedCategories} / {@code excludeWriteTools}
 * fields shape which tools the registration filter exposes to MCP clients. The default
 * (empty includes, empty excludes, write tools enabled) registers everything supported
 * by the running Minecraft target. See {@link com.chapmanjw.minecraft.fabric.mcp.compat.ToolCategory}
 * for the category list.
 */
public record Config(
        String host,
        int port,
        boolean authRequired,
        String bearerToken,
        boolean allowRemote,
        List<String> allowedOrigins,
        long commandTimeoutMs,
        int rateLimitRpm,
        int maxBodyBytes,
        int eventBufferSize,
        int queueMax,
        String logLevel,
        String tlsCertPath,
        String tlsKeyPath,
        boolean metricsEnabled,
        List<String> includedCategories,
        List<String> excludedCategories,
        boolean excludeWriteTools) {

    public Config {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(logLevel, "logLevel");
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        includedCategories = includedCategories == null ? List.of() : List.copyOf(includedCategories);
        excludedCategories = excludedCategories == null ? List.of() : List.copyOf(excludedCategories);
    }

    /** True when {@code host} is a loopback alias (127.0.0.1, ::1, or "localhost"). */
    public boolean isLoopback() {
        return "127.0.0.1".equals(host) || "::1".equals(host) || "localhost".equalsIgnoreCase(host);
    }

    /** True when both TLS cert and key paths are set (and equal-or-both-null in validation). */
    public boolean tlsEnabled() {
        return tlsCertPath != null && tlsKeyPath != null;
    }

    /** Returns the user-facing endpoint URL, e.g. {@code http://127.0.0.1:8765}. */
    public String endpointBase() {
        String scheme = tlsEnabled() ? "https" : "http";
        // Use 127.0.0.1 over "localhost" in the log so users can copy-paste without
        // worrying about DNS / hosts-file weirdness on Windows.
        String displayHost = isLoopback() ? "127.0.0.1" : host;
        return scheme + "://" + displayHost + ":" + port;
    }

    /** Returns a copy of this config with the bearer token replaced. */
    public Config withBearerToken(String newToken) {
        return new Config(
                host,
                port,
                authRequired,
                newToken,
                allowRemote,
                allowedOrigins,
                commandTimeoutMs,
                rateLimitRpm,
                maxBodyBytes,
                eventBufferSize,
                queueMax,
                logLevel,
                tlsCertPath,
                tlsKeyPath,
                metricsEnabled,
                includedCategories,
                excludedCategories,
                excludeWriteTools);
    }

    /** Defaults documented in docs/configuration.md. */
    public static Config defaults() {
        return new Config(
                "127.0.0.1",
                8765,
                false,
                null,
                false,
                List.of(),
                15_000L,
                60,
                16 * 1024 * 1024,
                1024,
                256,
                "info",
                null,
                null,
                false,
                List.of(),
                List.of(),
                false);
    }
}
