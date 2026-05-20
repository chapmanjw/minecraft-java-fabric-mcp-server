package com.chapmanjw.minecraft.fabric.mcp.protocol.error;

/**
 * JSON-RPC 2.0 standard codes plus MCP-specific extensions.
 *
 * <p>The standard JSON-RPC range is reserved (-32768..-32000). MCP application-level
 * errors use codes in the -32099..-32000 server error range as recommended by the
 * spec, plus a positive range for handler-specific errors that clients may want to
 * surface differently from protocol errors.
 */
public final class ErrorCodes {

    private ErrorCodes() {}

    // --- Standard JSON-RPC 2.0 ---------------------------------------------
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    // --- MCP / server errors ----------------------------------------------
    /** Tool was found in the registry but its arguments failed validation. */
    public static final int TOOL_INPUT_INVALID = -32001;

    /** Tool returned a structured error from its handler. */
    public static final int TOOL_HANDLER_ERROR = -32002;

    /** Main-thread work timed out. */
    public static final int MAIN_THREAD_TIMEOUT = -32003;

    /** Minecraft server is not currently running (no integrated world loaded). */
    public static final int SERVER_NOT_RUNNING = -32004;

    /** Tool exists but is not registered against the running Minecraft version. */
    public static final int TOOL_NOT_COMPATIBLE = -32005;
}
