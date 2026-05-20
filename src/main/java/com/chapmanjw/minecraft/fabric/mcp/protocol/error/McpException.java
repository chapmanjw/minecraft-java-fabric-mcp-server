package com.chapmanjw.minecraft.fabric.mcp.protocol.error;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Application-layer exception that carries a structured JSON-RPC error code and
 * an optional {@code data} payload. The dispatcher catches this and produces a
 * canonical JSON-RPC error response without leaking stack traces to clients.
 */
public final class McpException extends RuntimeException {

    private final int code;
    private final transient JsonNode data;

    public McpException(int code, String message) {
        this(code, message, (JsonNode) null);
    }

    public McpException(int code, String message, JsonNode data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public McpException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.data = null;
    }

    public int code() {
        return code;
    }

    public JsonNode data() {
        return data;
    }
}
