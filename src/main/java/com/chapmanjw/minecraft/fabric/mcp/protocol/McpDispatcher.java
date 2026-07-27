package com.chapmanjw.minecraft.fabric.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chapmanjw.minecraft.fabric.mcp.compat.ToolAccess;
import com.chapmanjw.minecraft.fabric.mcp.compat.ToolDescriptor;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;

/**
 * Dispatches JSON-RPC 2.0 requests to MCP method handlers.
 *
 * <p>Supported methods (per modelcontextprotocol.io spec, revision 2025-06-18):
 *
 * <ul>
 *   <li>{@code initialize} — negotiate protocol version, return server info + capabilities
 *   <li>{@code notifications/initialized} — client-acknowledges initialization (no response)
 *   <li>{@code ping} — keep-alive, returns empty result
 *   <li>{@code tools/list} — list registered tools
 *   <li>{@code tools/call} — invoke a tool by name with arguments
 * </ul>
 *
 * <p>This dispatcher does NOT manage HTTP sessions; the {@link McpHttpRoute} above
 * it handles transport-level concerns.
 */
public final class McpDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft_fabric_mcp/dispatcher");

    /** MCP protocol revision this server implements. */
    public static final String PROTOCOL_VERSION = "2025-06-18";

    private final ToolRegistry registry;
    private final ToolContext context;
    private final ObjectMapper mapper;
    private final ServerInfo serverInfo;

    public McpDispatcher(
            ToolRegistry registry, ToolContext context, ObjectMapper mapper, ServerInfo serverInfo) {
        this.registry = registry;
        this.context = context;
        this.mapper = mapper;
        this.serverInfo = serverInfo;
    }

    /**
     * Process one JSON-RPC request and return the response as a {@link JsonNode}.
     * Returns {@code null} for notifications (no id).
     */
    public JsonNode handle(JsonNode requestNode) {
        if (requestNode == null) {
            return errorResponse(null, ErrorCodes.PARSE_ERROR, "Empty request", null);
        }
        if (requestNode.isArray()) {
            // Batched requests aren't part of MCP's required surface; we accept them
            // by handling each element individually and returning an array.
            if (requestNode.size() == 0) {
                // JSON-RPC 2.0 §6: an empty batch is itself an Invalid Request — the
                // server MUST respond with a single error object, not 204 / empty.
                return errorResponse(null, ErrorCodes.INVALID_REQUEST, "Empty batch", null);
            }
            ArrayNode batchResponse = JsonNodeFactory.instance.arrayNode();
            for (JsonNode child : requestNode) {
                JsonNode resp = handle(child);
                if (resp != null) {
                    batchResponse.add(resp);
                }
            }
            // A batch where every entry was a notification yields an empty response
            // array; per §6 that's a "Nothing to return" situation — respond with
            // no envelope (the route layer will turn this into 204).
            return batchResponse.size() == 0 ? null : batchResponse;
        }

        JsonNode id = requestNode.get("id");
        String method = requestNode.path("method").asText(null);
        JsonNode params = requestNode.get("params");

        if (method == null) {
            return errorResponse(id, ErrorCodes.INVALID_REQUEST, "Missing 'method'", null);
        }

        try {
            JsonNode result = dispatch(method, params);
            if (id == null || id.isNull()) {
                return null; // notification — no response.
            }
            ObjectNode resp = mapper.createObjectNode();
            resp.put("jsonrpc", "2.0");
            resp.set("id", id);
            resp.set("result", result);
            return resp;
        } catch (McpException me) {
            return errorResponse(id, me.code(), me.getMessage(), me.data());
        } catch (Exception e) {
            LOGGER.warn("Unhandled dispatch error in '{}'", method, e);
            return errorResponse(
                    id,
                    ErrorCodes.INTERNAL_ERROR,
                    "Internal server error: " + e.getMessage(),
                    null);
        }
    }

    // --- method routing ------------------------------------------------------

    private JsonNode dispatch(String method, JsonNode params) throws Exception {
        return switch (method) {
            case "initialize" -> handleInitialize(params);
            case "notifications/initialized", "initialized" -> JsonNodeFactory.instance.objectNode();
            case "ping" -> JsonNodeFactory.instance.objectNode();
            case "tools/list" -> handleToolsList();
            case "tools/call" -> handleToolsCall(params);
            default -> throw new McpException(
                    ErrorCodes.METHOD_NOT_FOUND, "Method not found: " + method);
        };
    }

    // --- initialize ----------------------------------------------------------

    private JsonNode handleInitialize(JsonNode params) {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);

        ObjectNode capabilities = result.putObject("capabilities");
        ObjectNode tools = capabilities.putObject("tools");
        tools.put("listChanged", false);
        // We have no resources or prompts in v1; omit those capability blocks entirely
        // (the spec interprets omission as "not supported").

        ObjectNode info = result.putObject("serverInfo");
        info.put("name", serverInfo.name());
        info.put("version", serverInfo.version());

        if (serverInfo.instructions() != null && !serverInfo.instructions().isBlank()) {
            result.put("instructions", serverInfo.instructions());
        }
        return result;
    }

    // --- tools/list ----------------------------------------------------------

    private JsonNode handleToolsList() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode arr = result.putArray("tools");
        for (ToolRegistry.Entry entry : registry.list()) {
            ObjectNode toolNode = mapper.createObjectNode();
            toolNode.put("name", entry.descriptor().name());
            toolNode.put("description", entry.descriptor().description());
            toolNode.put("title", displayTitle(entry.descriptor().name()));
            toolNode.set("inputSchema", entry.tool().inputSchema());
            JsonNode outputSchema = entry.tool().outputSchema();
            if (outputSchema != null) {
                toolNode.set("outputSchema", outputSchema);
            }
            toolNode.set("annotations", annotationsFor(entry.descriptor()));
            arr.add(toolNode);
        }
        return result;
    }

    /**
     * Behaviour hints for one tool, per the MCP {@code ToolAnnotations} shape.
     *
     * <p>Emitting these is not cosmetic. Every field defaults in the direction of caution when
     * omitted -- {@code destructiveHint} defaults to TRUE and {@code openWorldHint} to TRUE -- so
     * a server that sends no annotations is telling every client that all 188 of its tools may
     * destroy things and may reach arbitrary external systems. For a surface that is mostly
     * read-only inspection of a local world, staying silent is actively misleading.
     *
     * <p>The mapping is derived from {@link ToolAccess}, which the registry already computes:
     *
     * <ul>
     *   <li>{@code readOnlyHint} -- true only for READ. That is the level's definition.
     *   <li>{@code destructiveHint} -- false for READ (it performs no updates at all), true for
     *       WRITE and ADMIN. WRITE is genuinely destructive rather than merely additive: filling a
     *       region overwrites whatever occupied it, and setting a block state replaces the block
     *       that was there. Claiming otherwise would understate the risk.
     *   <li>{@code openWorldHint} -- false everywhere. The entire domain of interaction is one
     *       Minecraft server this mod is running inside. Nothing here reaches an external system.
     *   <li>{@code idempotentHint} -- true only for READ, where repeating a call cannot change
     *       anything. It is deliberately NOT set for WRITE, because idempotency varies per tool
     *       and cannot be derived from the access level: setting a block state twice is
     *       idempotent, but summoning an entity or giving an item twice is not. Omitting it
     *       falls back to the spec default of false, which is the safe reading.
     * </ul>
     */
    private ObjectNode annotationsFor(ToolDescriptor descriptor) {
        ObjectNode annotations = mapper.createObjectNode();
        annotations.put("title", displayTitle(descriptor.name()));
        boolean readOnly = descriptor.access() == ToolAccess.READ;
        annotations.put("readOnlyHint", readOnly);
        annotations.put("destructiveHint", !readOnly);
        annotations.put("openWorldHint", false);
        if (readOnly) {
            annotations.put("idempotentHint", true);
        }
        return annotations;
    }

    /** Acronyms that would otherwise title-case into nonsense ("Poi", "Nbt", "Motd"). */
    private static final java.util.Map<String, String> TITLE_ACRONYMS =
            java.util.Map.ofEntries(
                    java.util.Map.entry("poi", "POI"),
                    java.util.Map.entry("nbt", "NBT"),
                    java.util.Map.entry("snbt", "SNBT"),
                    java.util.Map.entry("mcp", "MCP"),
                    java.util.Map.entry("xp", "XP"),
                    java.util.Map.entry("motd", "MOTD"),
                    java.util.Map.entry("tps", "TPS"),
                    java.util.Map.entry("uuid", "UUID"),
                    java.util.Map.entry("nether", "Nether"),
                    java.util.Map.entry("png", "PNG"));

    /**
     * Turns a snake_case tool id into a human-readable title, e.g. {@code level_poi_query} into
     * "Level POI Query". Clients display this instead of the raw id where they have room for it.
     */
    private static String displayTitle(String toolName) {
        StringBuilder out = new StringBuilder(toolName.length() + 4);
        for (String part : toolName.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            String acronym = TITLE_ACRONYMS.get(part);
            if (acronym != null) {
                out.append(acronym);
            } else {
                out.append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
            }
        }
        return out.toString();
    }

    // --- tools/call ----------------------------------------------------------

    private JsonNode handleToolsCall(JsonNode params) throws Exception {
        if (params == null || !params.isObject()) {
            throw new McpException(ErrorCodes.INVALID_PARAMS, "tools/call requires an object 'params'");
        }
        String name = params.path("name").asText(null);
        if (name == null || name.isEmpty()) {
            throw new McpException(ErrorCodes.INVALID_PARAMS, "tools/call: 'name' is required");
        }
        JsonNode arguments = params.get("arguments");

        ToolRegistry.Entry entry =
                registry.lookup(name)
                        .orElseThrow(
                                () ->
                                        new McpException(
                                                ErrorCodes.METHOD_NOT_FOUND,
                                                "Tool '" + name + "' is not registered"));
        ToolResult toolResult = entry.tool().execute(arguments, context);
        return toMcpResult(toolResult);
    }

    private ObjectNode toMcpResult(ToolResult toolResult) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode contentArr = result.putArray("content");
        for (JsonNode block : toolResult.content()) {
            contentArr.add(block);
        }
        if (toolResult.isError()) {
            result.put("isError", true);
        }
        return result;
    }

    // --- helpers -------------------------------------------------------------

    private ObjectNode errorResponse(JsonNode id, int code, String message, JsonNode data) {
        ObjectNode resp = mapper.createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (id == null || id.isNull()) {
            resp.putNull("id");
        } else {
            resp.set("id", id);
        }
        ObjectNode err = resp.putObject("error");
        err.put("code", code);
        err.put("message", message);
        if (data != null) {
            err.set("data", data);
        }
        return resp;
    }

    /** Static server info handed back from {@code initialize}. */
    public record ServerInfo(String name, String version, String instructions) {}
}
