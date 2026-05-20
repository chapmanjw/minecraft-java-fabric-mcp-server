package com.chapmanjw.minecraft.fabric.mcp.transport;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable HTTP response built by route handlers.
 *
 * <p>For Server-Sent Events / streaming responses, the protocol layer holds the
 * exchange open and writes incrementally; this class represents a one-shot response.
 */
public final class HttpResponse {

    private final int status;
    private final Map<String, String> headers;
    private final byte[] body;

    private HttpResponse(int status, Map<String, String> headers, byte[] body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    public int status() {
        return status;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public byte[] body() {
        return body;
    }

    public static HttpResponse json(int status, String body) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "application/json; charset=utf-8");
        h.put("Cache-Control", "no-store");
        return new HttpResponse(status, h, body.getBytes(StandardCharsets.UTF_8));
    }

    public static HttpResponse text(int status, String body) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "text/plain; charset=utf-8");
        h.put("Cache-Control", "no-store");
        return new HttpResponse(status, h, body.getBytes(StandardCharsets.UTF_8));
    }

    public static HttpResponse empty(int status) {
        return new HttpResponse(status, new LinkedHashMap<>(), new byte[0]);
    }

    /** Builder for responses that need custom headers. */
    public static Builder builder(int status) {
        return new Builder(status);
    }

    public static final class Builder {
        private final int status;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private byte[] body = new byte[0];

        private Builder(int status) {
            this.status = status;
        }

        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public Builder json(String b) {
            this.body = b.getBytes(StandardCharsets.UTF_8);
            headers.put("Content-Type", "application/json; charset=utf-8");
            return this;
        }

        public Builder text(String b) {
            this.body = b.getBytes(StandardCharsets.UTF_8);
            headers.put("Content-Type", "text/plain; charset=utf-8");
            return this;
        }

        public Builder bytes(byte[] b, String contentType) {
            this.body = b;
            headers.put("Content-Type", contentType);
            return this;
        }

        public HttpResponse build() {
            if (!headers.containsKey("Cache-Control")) {
                headers.put("Cache-Control", "no-store");
            }
            return new HttpResponse(status, headers, body);
        }
    }
}
