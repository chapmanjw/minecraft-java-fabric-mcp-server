package com.chapmanjw.minecraft.fabric.mcp.transport;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.chapmanjw.minecraft.fabric.mcp.config.Config;

/**
 * Pre-routing checks applied to every incoming request: {@code Host} validation,
 * {@code Origin} validation, and optional bearer authentication.
 *
 * <p>The {@code Host} check defends against DNS rebinding. When the server is bound
 * to a loopback address, only {@code localhost:<port>} and {@code 127.0.0.1:<port>}
 * are accepted. A malicious site that resolves an attacker-controlled domain to
 * 127.0.0.1 will send {@code Host: attacker.com}, which this filter rejects.
 *
 * <p>The {@code Origin} check defends against CSRF from browsers. By default the
 * allowed origins list is empty, meaning any request that carries an {@code Origin}
 * header is rejected. Legitimate MCP clients ({@code mcp-remote}, Cursor) do not
 * send {@code Origin}; browsers always do for cross-origin requests.
 *
 * <p>The class returns a {@link Decision} rather than throwing — keeps the dispatcher
 * loop straight and lets the transport log structured rejection metrics.
 */
public final class SecurityFilter {

    private final Config config;

    public SecurityFilter(Config config) {
        this.config = config;
    }

    /** Result of applying the filter to a single request. */
    public sealed interface Decision {
        record Allowed() implements Decision {}

        record Rejected(int status, String reason) implements Decision {}
    }

    public Decision evaluate(HttpRequest request) {
        Decision host = checkHost(request);
        if (host instanceof Decision.Rejected) {
            return host;
        }
        Decision origin = checkOrigin(request);
        if (origin instanceof Decision.Rejected) {
            return origin;
        }
        Decision body = checkBodySize(request);
        if (body instanceof Decision.Rejected) {
            return body;
        }
        return checkAuth(request);
    }

    // --- Host ---------------------------------------------------------------

    private Decision checkHost(HttpRequest request) {
        Optional<String> host = request.header("Host");
        if (host.isEmpty()) {
            // RFC 7230 requires a Host header on HTTP/1.1; reject as a malformed request.
            return new Decision.Rejected(400, "Missing Host header");
        }
        String value = host.get().toLowerCase(Locale.ROOT);
        // Strip port for compare? No — we want host:port matching so a request with no
        // port (or the wrong port) is also rejected. Construct expected values.
        if (config.isLoopback()) {
            String expected1 = "localhost:" + config.port();
            String expected2 = "127.0.0.1:" + config.port();
            String expected3 = "[::1]:" + config.port();
            if (value.equals(expected1) || value.equals(expected2) || value.equals(expected3)) {
                return new Decision.Allowed();
            }
            return new Decision.Rejected(403, "Host header '" + host.get() + "' not in allowed set");
        }
        // Non-loopback: accept Host headers that match the configured bind host:port.
        String expected = config.host().toLowerCase(Locale.ROOT) + ":" + config.port();
        if (value.equals(expected)) {
            return new Decision.Allowed();
        }
        return new Decision.Rejected(403, "Host header '" + host.get() + "' does not match bind address");
    }

    // --- Origin -------------------------------------------------------------

    private Decision checkOrigin(HttpRequest request) {
        Optional<String> originHeader = request.header("Origin");
        if (originHeader.isEmpty()) {
            // No Origin → safe (legitimate non-browser MCP clients).
            return new Decision.Allowed();
        }
        List<String> allowed = config.allowedOrigins();
        if (allowed.isEmpty()) {
            return new Decision.Rejected(
                    403,
                    "Origin header present but allowed_origins is empty. Add the origin to"
                            + " allowed_origins in config.json to permit browser-style clients.");
        }
        String origin = originHeader.get();
        for (String allowedOrigin : allowed) {
            if (allowedOrigin.equals(origin)) {
                return new Decision.Allowed();
            }
        }
        return new Decision.Rejected(403, "Origin '" + origin + "' not in allowed_origins");
    }

    // --- Body size ----------------------------------------------------------

    private Decision checkBodySize(HttpRequest request) {
        if (request.contentLength() > config.maxBodyBytes()) {
            return new Decision.Rejected(
                    413, "Request body exceeds max_body_bytes=" + config.maxBodyBytes());
        }
        return new Decision.Allowed();
    }

    // --- Auth ---------------------------------------------------------------

    private Decision checkAuth(HttpRequest request) {
        if (!config.authRequired()) {
            return new Decision.Allowed();
        }
        Optional<String> auth = request.header("Authorization");
        if (auth.isEmpty()) {
            return new Decision.Rejected(401, "Authorization header required");
        }
        String value = auth.get();
        String prefix = "Bearer ";
        if (!value.startsWith(prefix)) {
            return new Decision.Rejected(401, "Authorization header must use Bearer scheme");
        }
        String supplied = value.substring(prefix.length()).trim();
        if (!ConstantTimeEquals.equals(supplied, config.bearerToken())) {
            return new Decision.Rejected(401, "Invalid bearer token");
        }
        return new Decision.Allowed();
    }

    /**
     * Returns the rate-limit bucket key for a request. When auth is on, we key by a
     * stable identifier derived from the token so multiple connections from the same
     * client share one bucket; otherwise we key by remote IP.
     */
    public String rateLimitKey(HttpRequest request) {
        if (config.authRequired()) {
            // Identifier is the first 16 chars of the token (already validated above).
            // We don't expose the full token in metrics/logs.
            String supplied =
                    request.header("Authorization")
                            .map(v -> v.startsWith("Bearer ") ? v.substring(7).trim() : v)
                            .orElse("");
            return "tok:" + supplied.substring(0, Math.min(16, supplied.length()));
        }
        return "ip:"
                + (request.remoteAddress() == null
                        ? "unknown"
                        : request.remoteAddress().getAddress().getHostAddress());
    }
}
