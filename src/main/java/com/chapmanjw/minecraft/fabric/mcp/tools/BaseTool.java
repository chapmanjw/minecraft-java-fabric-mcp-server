package com.chapmanjw.minecraft.fabric.mcp.tools;

import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.ArgumentReader;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Tool;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.runtime.MinecraftMainThreadExecutor;

/**
 * Convenience base for tool implementations.
 *
 * <p>Subclasses get:
 *
 * <ul>
 *   <li>{@link #onMainThread(ToolContext, Function)} — submit a piece of work to the
 *       Minecraft server main thread and unwrap exceptions consistently.
 *   <li>{@link #reader(String, JsonNode)} — typed argument access with consistent
 *       MCP-style error messages.
 *   <li>{@link #ok(String)} — short builder for a single-text-block result.
 * </ul>
 */
public abstract class BaseTool implements Tool {

    /** Annotation name; cached for error messages and main-thread submission tags. */
    protected final String toolName;

    protected BaseTool(String toolName) {
        this.toolName = toolName;
    }

    protected ArgumentReader reader(JsonNode arguments) {
        return new ArgumentReader(toolName, arguments);
    }

    protected static ToolResult ok(String message) {
        return ToolResult.ofText(message);
    }

    /**
     * Return a structured payload as TOON-encoded text. Replaces the previous "text
     * summary + JSON structured" dual emission — TOON is dense enough that the human
     * summary is unnecessary noise and clients can parse TOON directly. Prefer this
     * over {@link #ok(String)} for any non-trivial structured response.
     */
    protected static ToolResult okToon(JsonNode payload) {
        return ToolResult.ofToon(payload);
    }

    /**
     * Schedule {@code work} on the Minecraft main thread, wait up to the configured
     * timeout, and return its result. Translates checked exceptions to
     * {@link McpException} with appropriate codes so the caller can be a clean lambda.
     */
    protected <R> R onMainThread(ToolContext context, Function<Void, R> work) {
        MinecraftMainThreadExecutor exec = context.mainThreadExecutor();
        try {
            return exec.submitBlocking(() -> work.apply(null));
        } catch (TimeoutException te) {
            throw new McpException(
                    ErrorCodes.MAIN_THREAD_TIMEOUT,
                    "Tool '" + toolName + "' timed out waiting on the Minecraft main thread");
        } catch (MinecraftMainThreadExecutor.MainThreadWorkException mwe) {
            Throwable cause = mwe.getCause();
            String msg = (cause == null || cause.getMessage() == null) ? mwe.getMessage() : cause.getMessage();
            throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR, "Tool '" + toolName + "' failed: " + msg, cause);
        }
    }
}
