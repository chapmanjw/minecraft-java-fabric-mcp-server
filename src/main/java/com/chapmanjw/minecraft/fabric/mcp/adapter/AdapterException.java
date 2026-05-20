package com.chapmanjw.minecraft.fabric.mcp.adapter;

/**
 * Raised by the adapter when an operation cannot complete — bad argument, missing
 * entity, or "not yet implemented for this target version".
 *
 * <p>Tool handlers catch this and convert it to an appropriate JSON-RPC error so the
 * MCP client sees a clear message rather than a stack trace.
 */
public final class AdapterException extends RuntimeException {

    public AdapterException(String message) {
        super(message);
    }

    public AdapterException(String message, Throwable cause) {
        super(message, cause);
    }
}
