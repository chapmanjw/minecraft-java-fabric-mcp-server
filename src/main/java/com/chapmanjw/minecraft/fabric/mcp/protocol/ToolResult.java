package com.chapmanjw.minecraft.fabric.mcp.protocol;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builder for {@code tools/call} results.
 *
 * <p>Per MCP spec revision 2025-06-18, a tool result is a list of content blocks (text,
 * image, resource). This server emits structured payloads as TOON-encoded text in a
 * single text block — TOON cuts ~40% of tokens vs the equivalent JSON while remaining
 * trivially parseable by any LLM client. The MCP-spec {@code structuredContent} field
 * is intentionally NOT emitted; clients that want machine-parseable output decode the
 * TOON text. See {@link Toon} for the serialization format.
 */
public final class ToolResult {

    private final List<JsonNode> content = new ArrayList<>();
    private boolean isError;

    private ToolResult() {}

    public static ToolResult create() {
        return new ToolResult();
    }

    /** Add a plain text content block. */
    public ToolResult addText(String text) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "text");
        node.put("text", text == null ? "" : text);
        content.add(node);
        return this;
    }

    /** Add an image content block (MCP {@code image} type) from raw bytes. */
    public ToolResult addImage(byte[] data, String mimeType) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "image");
        node.put("data", java.util.Base64.getEncoder().encodeToString(data == null ? new byte[0] : data));
        node.put("mimeType", mimeType == null ? "image/png" : mimeType);
        content.add(node);
        return this;
    }

    /** Mark the result as an error. The {@code content[]} should explain what went wrong. */
    public ToolResult markError() {
        this.isError = true;
        return this;
    }

    public List<JsonNode> content() {
        return content;
    }

    public boolean isError() {
        return isError;
    }

    /** Quick constructor for the most common case: one text block, no error. */
    public static ToolResult ofText(String text) {
        return create().addText(text);
    }

    /**
     * Quick constructor for a structured payload — encodes {@code payload} as TOON and
     * wraps it in a single text content block. This is the preferred way to return any
     * non-trivial structured data.
     */
    public static ToolResult ofToon(JsonNode payload) {
        return create().addText(Toon.encode(payload));
    }

    /** Quick constructor for a single image content block (e.g. a PNG render). */
    public static ToolResult ofImage(byte[] data, String mimeType) {
        return create().addImage(data, mimeType);
    }
}
