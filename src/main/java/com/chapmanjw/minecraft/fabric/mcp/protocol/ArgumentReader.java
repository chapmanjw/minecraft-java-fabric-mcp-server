package com.chapmanjw.minecraft.fabric.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;

/**
 * Type-safe accessor for tool argument trees with consistent {@link McpException}
 * messages on validation failure.
 *
 * <p>Use this in tool handlers rather than reaching for {@link JsonNode#path}
 * directly — the consistent error messages help users debug failed calls without
 * trial-and-error.
 */
public final class ArgumentReader {

    private final JsonNode root;
    private final String toolName;

    public ArgumentReader(String toolName, JsonNode root) {
        this.toolName = toolName;
        this.root = root == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode() : root;
    }

    public JsonNode raw() {
        return root;
    }

    // --- required --------------------------------------------------------

    public String requireString(String name) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            throw missing(name);
        }
        if (!n.isTextual()) {
            throw mismatch(name, "string");
        }
        return n.asText();
    }

    public int requireInt(String name) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            throw missing(name);
        }
        // Reject non-integral numbers (1.5 must not silently truncate to 1).
        if (!n.isIntegralNumber() || !n.canConvertToInt()) {
            throw mismatch(name, "integer");
        }
        return n.intValue();
    }

    public long requireLong(String name) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            throw missing(name);
        }
        if (!n.isIntegralNumber() || !n.canConvertToLong()) {
            throw mismatch(name, "long");
        }
        return n.longValue();
    }

    public double requireDouble(String name) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            throw missing(name);
        }
        if (!n.isNumber()) {
            throw mismatch(name, "number");
        }
        return n.doubleValue();
    }

    public boolean requireBoolean(String name) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            throw missing(name);
        }
        if (!n.isBoolean()) {
            throw mismatch(name, "boolean");
        }
        return n.booleanValue();
    }

    public JsonNode requireObject(String name) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            throw missing(name);
        }
        if (!n.isObject()) {
            throw mismatch(name, "object");
        }
        return n;
    }

    public JsonNode requireArray(String name) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            throw missing(name);
        }
        if (!n.isArray()) {
            throw mismatch(name, "array");
        }
        return n;
    }

    // --- optional --------------------------------------------------------

    public String optString(String name, String fallback) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            return fallback;
        }
        if (!n.isTextual()) {
            throw mismatch(name, "string");
        }
        return n.asText();
    }

    public int optInt(String name, int fallback) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            return fallback;
        }
        // Same float-rejection rule as requireInt — float `1.5` should not silently
        // become `1` just because the argument is optional.
        if (!n.isIntegralNumber() || !n.canConvertToInt()) {
            throw mismatch(name, "integer");
        }
        return n.intValue();
    }

    public long optLong(String name, long fallback) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            return fallback;
        }
        if (!n.isIntegralNumber() || !n.canConvertToLong()) {
            throw mismatch(name, "long");
        }
        return n.longValue();
    }

    public double optDouble(String name, double fallback) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            return fallback;
        }
        if (!n.isNumber()) {
            throw mismatch(name, "number");
        }
        return n.doubleValue();
    }

    public boolean optBoolean(String name, boolean fallback) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            return fallback;
        }
        if (!n.isBoolean()) {
            throw mismatch(name, "boolean");
        }
        return n.booleanValue();
    }

    public JsonNode optObject(String name) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            return null;
        }
        if (!n.isObject()) {
            throw mismatch(name, "object");
        }
        return n;
    }

    public JsonNode optArray(String name) {
        JsonNode n = root.get(name);
        if (n == null || n.isNull()) {
            return null;
        }
        if (!n.isArray()) {
            throw mismatch(name, "array");
        }
        return n;
    }

    public boolean has(String name) {
        JsonNode n = root.get(name);
        return n != null && !n.isNull();
    }

    // --- errors ----------------------------------------------------------

    private McpException missing(String name) {
        return new McpException(
                ErrorCodes.TOOL_INPUT_INVALID,
                "Tool '" + toolName + "': missing required argument '" + name + "'");
    }

    private McpException mismatch(String name, String expected) {
        return new McpException(
                ErrorCodes.TOOL_INPUT_INVALID,
                "Tool '" + toolName + "': argument '" + name + "' must be a " + expected);
    }
}
