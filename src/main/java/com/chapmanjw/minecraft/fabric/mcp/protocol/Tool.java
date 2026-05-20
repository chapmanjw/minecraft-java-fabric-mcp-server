package com.chapmanjw.minecraft.fabric.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP tool implementation contract.
 *
 * <p>Tools are typically annotated with
 * {@link com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool} and registered into the
 * {@link ToolRegistry} at startup after passing the
 * {@link com.chapmanjw.minecraft.fabric.mcp.compat.ToolCompatibilityFilter}.
 *
 * <p>Implementations are stateless once constructed; the registry uses one instance
 * per tool, shared across all calls.
 */
public interface Tool {

    /**
     * JSON Schema fragment describing this tool's {@code arguments} object. Returned
     * verbatim in {@code tools/list} responses. Implementations typically construct
     * this once at class init via {@link Schemas} helpers.
     */
    JsonNode inputSchema();

    /**
     * Optional JSON Schema fragment describing the {@code structuredContent} field of
     * a successful response. Returning {@code null} signals the tool only emits text
     * content, which MCP clients accept.
     */
    default JsonNode outputSchema() {
        return null;
    }

    /**
     * Execute the tool against the supplied {@code context} with the parsed argument
     * tree. Implementations MUST submit any Minecraft API access through
     * {@link ToolContext#mainThreadExecutor()} — see docs/architecture.md.
     *
     * @return the structured result. The dispatcher wraps it in the MCP response envelope.
     * @throws com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException for structured failures
     */
    ToolResult execute(JsonNode arguments, ToolContext context) throws Exception;
}
