package com.chapmanjw.minecraft.fabric.mcp.config;

import java.util.List;
import java.util.Objects;

import com.chapmanjw.minecraft.fabric.mcp.compat.ToolAccess;

/**
 * Immutable configuration for the MCP server.
 *
 * <p>Created by {@link ConfigLoader} after applying file values, environment variable
 * overrides, validation, and (if {@code authRequired} and no token is supplied) bearer
 * token generation. Treat instances as authoritative — every layer downstream reads
 * from this record without re-resolving environment variables.
 *
 * <p>The {@code includedCategories} / {@code excludedCategories} / {@code maxAccess} /
 * {@code excludeWriteTools} fields shape which tools the registration filter exposes to
 * MCP clients:
 *
 * <ul>
 *   <li>If {@code includedCategories} is non-empty it is the allowlist; otherwise the
 *       default-on categories ({@link com.chapmanjw.minecraft.fabric.mcp.compat.ToolCategory#enabledByDefault()})
 *       apply.
 *   <li>{@code excludedCategories} is then subtracted.
 *   <li>Finally every tool whose access rank exceeds {@code maxAccess} is dropped.
 * </ul>
 *
 * <p>{@code maxAccess} defaults to {@code "write"} — admin tools are opt-in. The legacy
 * {@code excludeWriteTools=true} is equivalent to {@code maxAccess=read} and, when set,
 * lowers the effective cap to {@code read}. See
 * {@link com.chapmanjw.minecraft.fabric.mcp.compat.ToolCategory} for the category list
 * and {@link com.chapmanjw.minecraft.fabric.mcp.compat.ToolAccess} for the access axis.
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
        String maxAccess,
        boolean excludeWriteTools) {

    public Config {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(logLevel, "logLevel");
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        includedCategories = includedCategories == null ? List.of() : List.copyOf(includedCategories);
        excludedCategories = excludedCategories == null ? List.of() : List.copyOf(excludedCategories);
        maxAccess = (maxAccess == null || maxAccess.isBlank()) ? ToolAccess.WRITE.wireName() : maxAccess;
    }

    /** True when {@code host} is a loopback alias (127.0.0.1, ::1, or "localhost"). */
    public boolean isLoopback() {
        return "127.0.0.1".equals(host) || "::1".equals(host) || "localhost".equalsIgnoreCase(host);
    }

    /** True when both TLS cert and key paths are set (and equal-or-both-null in validation). */
    public boolean tlsEnabled() {
        return tlsCertPath != null && tlsKeyPath != null;
    }

    /**
     * The effective access cap: parses {@code maxAccess} (defaulting to {@code WRITE} if
     * unparseable) and lowers it to {@code READ} when the legacy {@code excludeWriteTools}
     * is set. Downstream filters compare a tool's access rank against this.
     */
    public ToolAccess effectiveMaxAccess() {
        ToolAccess parsed = ToolAccess.fromWireName(maxAccess).orElse(ToolAccess.WRITE);
        if (excludeWriteTools && parsed.rank() > ToolAccess.READ.rank()) {
            return ToolAccess.READ;
        }
        return parsed;
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
                maxAccess,
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
                ToolAccess.WRITE.wireName(),
                false);
    }
}
