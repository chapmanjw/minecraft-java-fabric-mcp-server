package com.chapmanjw.minecraft.fabric.mcp.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chapmanjw.minecraft.fabric.mcp.transport.HttpMethod;
import com.chapmanjw.minecraft.fabric.mcp.transport.HttpRequest;
import com.chapmanjw.minecraft.fabric.mcp.transport.HttpResponse;
import com.chapmanjw.minecraft.fabric.mcp.transport.HttpRouteHandler;

/**
 * HTTP route that adapts MCP's Streamable HTTP transport to the JSON-RPC dispatcher.
 *
 * <p>Per the MCP spec (revision 2025-06-18):
 *
 * <ul>
 *   <li>{@code POST /mcp} — client sends a JSON-RPC request, server returns a JSON-RPC
 *       response (or 202 Accepted for notifications).
 *   <li>{@code GET /mcp} — open a server-to-client SSE stream for server-initiated
 *       messages. We accept this for forward compatibility but produce no messages
 *       (the v1 surface has no spontaneous server output).
 *   <li>{@code DELETE /mcp} — close the session. We treat it as a 204 No Content
 *       because we don't persist session state across requests.
 * </ul>
 *
 * <p>Session IDs ({@code Mcp-Session-Id} header) are accepted but ignored — the v1
 * dispatcher is stateless across requests, so resuming or attaching to a previous
 * session is a no-op.
 */
public final class McpHttpRoute implements HttpRouteHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft_fabric_mcp/route");

    private final McpDispatcher dispatcher;
    private final ObjectMapper mapper;

    public McpHttpRoute(McpDispatcher dispatcher, ObjectMapper mapper) {
        this.dispatcher = dispatcher;
        this.mapper = mapper;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        HttpMethod method = request.method();
        if (method == null) {
            return HttpResponse.json(400, "{\"error\":\"Unsupported HTTP method\"}");
        }
        return switch (method) {
            case POST -> handlePost(request);
            case GET -> handleGet();
            case DELETE -> HttpResponse.empty(204);
            case OPTIONS -> handlePreflight(request);
            default -> HttpResponse.json(
                    405, "{\"error\":\"Only POST/GET/DELETE/OPTIONS allowed on /mcp\"}");
        };
    }

    private HttpResponse handlePost(HttpRequest request) {
        if (request.contentLength() == 0) {
            return HttpResponse.json(400, "{\"error\":\"Empty request body\"}");
        }
        JsonNode body;
        try {
            body = mapper.readTree(request.body());
        } catch (JsonProcessingException e) {
            // Jackson's getMessage() often contains newlines, control chars, and quotes;
            // run it through the mapper to get a properly-escaped JSON string literal so
            // the response body stays valid JSON regardless of the parse-error wording.
            String escapedMessage;
            try {
                escapedMessage = mapper.writeValueAsString(e.getMessage());
            } catch (JsonProcessingException nested) {
                escapedMessage = "\"Malformed request body\"";
            }
            return HttpResponse.json(
                    400,
                    "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":"
                            + escapedMessage + "}}");
        } catch (Exception e) {
            return HttpResponse.json(400, "{\"error\":\"Malformed request body\"}");
        }

        JsonNode response = dispatcher.handle(body);
        if (response == null) {
            // Notification (no id) — RFC says respond with 204 No Content.
            return HttpResponse.empty(204);
        }
        try {
            String serialized = mapper.writeValueAsString(response);
            return HttpResponse.builder(200)
                    .json(serialized)
                    .header("Mcp-Session-Id", "stateless")
                    .build();
        } catch (JsonProcessingException e) {
            LOGGER.warn("Failed to serialize dispatcher response", e);
            return HttpResponse.json(
                    500,
                    "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,"
                            + "\"message\":\"Internal serialization error\"}}");
        }
    }

    /**
     * Server-to-client SSE stream. The v1 surface has no spontaneous messages, so we
     * accept the request, return an empty stream, and let the client tear it down.
     * Returning 405 would break MCP clients that proactively open the SSE channel.
     */
    private HttpResponse handleGet() {
        // The SSE body is a single comment line followed by \n\n so the EventSource
        // client's reader unblocks immediately. We use the .bytes() builder method
        // because .text() would clobber the text/event-stream Content-Type.
        byte[] sseBody = ": no spontaneous messages — close at will\n\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return HttpResponse.builder(200)
                .header("Cache-Control", "no-store")
                .bytes(sseBody, "text/event-stream; charset=utf-8")
                .build();
    }

    /**
     * Browser preflight. We never serve cross-origin in default config — but
     * answering OPTIONS politely avoids client retries.
     */
    private HttpResponse handlePreflight(HttpRequest request) {
        return HttpResponse.builder(204)
                .header("Allow", "POST, GET, DELETE, OPTIONS")
                .header(
                        "Access-Control-Allow-Methods",
                        request.header("Access-Control-Request-Method").orElse("POST"))
                .header(
                        "Access-Control-Allow-Headers",
                        request.header("Access-Control-Request-Headers").orElse("Authorization,Content-Type"))
                .header("Access-Control-Max-Age", "600")
                .build();
    }
}
