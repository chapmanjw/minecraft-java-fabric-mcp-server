package com.chapmanjw.minecraft.fabric.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chapmanjw.minecraft.fabric.mcp.compat.ToolCategory;
import com.chapmanjw.minecraft.fabric.mcp.compat.ToolDescriptor;
import com.chapmanjw.minecraft.fabric.mcp.transport.HttpMethod;
import com.chapmanjw.minecraft.fabric.mcp.transport.HttpRequest;
import com.chapmanjw.minecraft.fabric.mcp.transport.HttpResponse;

class McpHttpRouteTest {

    private ObjectMapper mapper;
    private McpHttpRoute route;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        registry.register(
                new ToolDescriptor(
                        "echo", "", "", "", List.of(), "",
                        ToolCategory.GAMEPLAY, false, EchoTool.class),
                new EchoTool());
        ToolContext ctx = new ToolContext(null, null, null, null, mapper);
        McpDispatcher dispatcher =
                new McpDispatcher(
                        registry,
                        ctx,
                        mapper,
                        new McpDispatcher.ServerInfo("t", "0", null));
        route = new McpHttpRoute(dispatcher, mapper);
    }

    private static HttpRequest request(HttpMethod m, byte[] body) {
        return request(m, body, Map.of());
    }

    private static HttpRequest request(HttpMethod m, byte[] body, Map<String, List<String>> headers) {
        return new HttpRequest(
                m, "/mcp", null, headers, body, new InetSocketAddress("127.0.0.1", 12345));
    }

    @Test
    void postWithValidJsonRpcReturns200AndSessionHeader() throws Exception {
        byte[] body =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"
                        .getBytes(StandardCharsets.UTF_8);
        HttpResponse r = route.handle(request(HttpMethod.POST, body));
        assertEquals(200, r.status());
        assertEquals("application/json; charset=utf-8", r.headers().get("Content-Type"));
        assertEquals("stateless", r.headers().get("Mcp-Session-Id"));
        JsonNode tree = mapper.readTree(r.body());
        assertEquals("2.0", tree.path("jsonrpc").asText());
        assertEquals(1, tree.path("id").asInt());
    }

    @Test
    void postEmptyBodyReturns400() {
        HttpResponse r = route.handle(request(HttpMethod.POST, new byte[0]));
        assertEquals(400, r.status());
    }

    @Test
    void postMalformedJsonReturns400Json() throws Exception {
        byte[] body = "{not valid json".getBytes(StandardCharsets.UTF_8);
        HttpResponse r = route.handle(request(HttpMethod.POST, body));
        assertEquals(400, r.status());
        // The response is a JSON-RPC-style error envelope.
        JsonNode tree = mapper.readTree(r.body());
        assertEquals(-32700, tree.path("error").path("code").asInt());
    }

    @Test
    void postNotificationReturns204() {
        byte[] body =
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"
                        .getBytes(StandardCharsets.UTF_8);
        HttpResponse r = route.handle(request(HttpMethod.POST, body));
        assertEquals(204, r.status());
    }

    @Test
    void getReturnsSseStream() {
        HttpResponse r = route.handle(request(HttpMethod.GET, new byte[0]));
        assertEquals(200, r.status());
        assertEquals("text/event-stream; charset=utf-8", r.headers().get("Content-Type"));
        assertEquals("no-store", r.headers().get("Cache-Control"));
        String text = new String(r.body(), StandardCharsets.UTF_8);
        assertTrue(text.startsWith(":"), "SSE comment expected at start: " + text);
    }

    @Test
    void deleteReturns204() {
        HttpResponse r = route.handle(request(HttpMethod.DELETE, new byte[0]));
        assertEquals(204, r.status());
    }

    @Test
    void optionsPreflightReflectsRequestedMethodAndHeaders() {
        HttpRequest req =
                request(
                        HttpMethod.OPTIONS,
                        new byte[0],
                        Map.of(
                                "Access-Control-Request-Method", List.of("POST"),
                                "Access-Control-Request-Headers", List.of("Authorization,X-Custom")));
        HttpResponse r = route.handle(req);
        assertEquals(204, r.status());
        assertEquals("POST, GET, DELETE, OPTIONS", r.headers().get("Allow"));
        assertEquals("POST", r.headers().get("Access-Control-Allow-Methods"));
        assertEquals(
                "Authorization,X-Custom",
                r.headers().get("Access-Control-Allow-Headers"));
        assertNotNull(r.headers().get("Access-Control-Max-Age"));
    }

    @Test
    void optionsPreflightUsesDefaultsWhenMissingRequestHeaders() {
        HttpResponse r = route.handle(request(HttpMethod.OPTIONS, new byte[0]));
        assertEquals(204, r.status());
        assertEquals("POST", r.headers().get("Access-Control-Allow-Methods"));
        assertEquals(
                "Authorization,Content-Type",
                r.headers().get("Access-Control-Allow-Headers"));
    }

    @Test
    void putReturns405() {
        HttpResponse r = route.handle(request(HttpMethod.PUT, new byte[0]));
        assertEquals(405, r.status());
    }

    @Test
    void unknownMethodReturns400() {
        HttpRequest req =
                new HttpRequest(
                        null,
                        "/mcp",
                        null,
                        Map.of(),
                        new byte[0],
                        new InetSocketAddress("127.0.0.1", 12345));
        HttpResponse r = route.handle(req);
        assertEquals(400, r.status());
    }

    /** Trivial tool used purely so dispatcher.handle() has something to dispatch to. */
    private static final class EchoTool implements Tool {
        @Override
        public JsonNode inputSchema() {
            return new ObjectMapper().createObjectNode().put("type", "object");
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            ObjectNode args = arguments == null ? null : (ObjectNode) arguments;
            String text = args == null ? "" : args.path("text").asText("");
            return ToolResult.ofText(text);
        }
    }
}
