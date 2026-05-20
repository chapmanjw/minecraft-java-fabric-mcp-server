package com.chapmanjw.minecraft.fabric.mcp.transport;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable view of an HTTP request after the transport layer has parsed and
 * size-checked the body.
 *
 * <p>The body is held as a byte array so handlers can decide whether to parse it as
 * UTF-8 text, JSON, or binary. {@link #bodyAsString()} is provided as a convenience
 * for the common UTF-8 case.
 */
public record HttpRequest(
        HttpMethod method,
        String path,
        String query,
        Map<String, List<String>> headers,
        byte[] body,
        InetSocketAddress remoteAddress) {

    /** Returns the first value of a header, case-insensitively. */
    public Optional<String> header(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (var e : headers.entrySet()) {
            if (e.getKey().toLowerCase(Locale.ROOT).equals(lower)) {
                List<String> vs = e.getValue();
                if (vs != null && !vs.isEmpty()) {
                    return Optional.of(vs.get(0));
                }
            }
        }
        return Optional.empty();
    }

    public String bodyAsString() {
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }

    public int contentLength() {
        return body == null ? 0 : body.length;
    }
}
