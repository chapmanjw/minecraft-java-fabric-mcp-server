package com.chapmanjw.minecraft.fabric.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chapmanjw.minecraft.fabric.mcp.compat.ToolCategory;
import com.chapmanjw.minecraft.fabric.mcp.compat.ToolDescriptor;

class McpDispatcherTest {

    private ObjectMapper mapper;
    private ToolRegistry registry;
    private McpDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        registry = new ToolRegistry();
        registry.register(
                new ToolDescriptor(
                        "echo", "Echo the input", "", "", java.util.List.of(), "",
                        ToolCategory.GAMEPLAY,
                        com.chapmanjw.minecraft.fabric.mcp.compat.ToolAccess.WRITE,
                        EchoTool.class),
                new EchoTool());
        ToolContext ctx = new ToolContext(null, null, null, null, mapper, null, null);
        dispatcher =
                new McpDispatcher(
                        registry,
                        ctx,
                        mapper,
                        new McpDispatcher.ServerInfo("test", "0.0.0", null));
    }

    @Test
    void initializeReturnsProtocolVersion() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        JsonNode resp = dispatcher.handle(req);
        assertNotNull(resp);
        assertEquals(McpDispatcher.PROTOCOL_VERSION, resp.path("result").path("protocolVersion").asText());
        assertEquals("test", resp.path("result").path("serverInfo").path("name").asText());
    }

    @Test
    void toolsListEmitsAnnotationsForAWriteTool() throws Exception {
        // The registered "echo" tool is WRITE. Every annotation field defaults in the cautious
        // direction when omitted (destructiveHint true, openWorldHint true), so the point of this
        // test is that we state them rather than let a client assume.
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/list\"}");
        JsonNode tool = dispatcher.handle(req).path("result").path("tools").get(0);
        JsonNode ann = tool.path("annotations");
        assertFalse(ann.isMissingNode(), "tools/list must emit an annotations object");
        assertFalse(ann.path("readOnlyHint").asBoolean(), "WRITE is not read-only");
        assertTrue(ann.path("destructiveHint").asBoolean(),
                "WRITE overwrites existing state, so it is destructive rather than additive");
        assertFalse(ann.path("openWorldHint").asBoolean(),
                "every tool acts on the local Minecraft server, never an external system");
        // Idempotency varies per write tool and cannot be derived from the access level, so it
        // must be left unset rather than guessed; the spec default of false is the safe reading.
        assertTrue(ann.path("idempotentHint").isMissingNode(),
                "idempotentHint must not be asserted for WRITE tools");
        assertEquals("Echo", ann.path("title").asText());
        assertEquals("Echo", tool.path("title").asText(), "title is also a top-level tool field");
    }

    @Test
    void toolsListMarksReadOnlyToolsAsSafe() throws Exception {
        ToolRegistry readRegistry = new ToolRegistry();
        readRegistry.register(
                new ToolDescriptor(
                        "level_poi_query", "Query POIs", "", "", java.util.List.of(), "",
                        ToolCategory.WORLD,
                        com.chapmanjw.minecraft.fabric.mcp.compat.ToolAccess.READ,
                        EchoTool.class),
                new EchoTool());
        McpDispatcher readDispatcher =
                new McpDispatcher(
                        readRegistry,
                        new ToolContext(null, null, null, null, mapper, null, null),
                        mapper,
                        new McpDispatcher.ServerInfo("test", "0.0.0", null));
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/list\"}");
        JsonNode tool = readDispatcher.handle(req).path("result").path("tools").get(0);
        JsonNode ann = tool.path("annotations");
        assertTrue(ann.path("readOnlyHint").asBoolean());
        assertFalse(ann.path("destructiveHint").asBoolean(), "a READ tool performs no updates");
        assertTrue(ann.path("idempotentHint").asBoolean(), "repeating a read changes nothing");
        // Acronyms would otherwise title-case into "Poi".
        assertEquals("Level POI Query", ann.path("title").asText());
    }

    @Test
    void toolsListIncludesRegisteredTool() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        JsonNode resp = dispatcher.handle(req);
        JsonNode tools = resp.path("result").path("tools");
        assertEquals(1, tools.size());
        assertEquals("echo", tools.get(0).path("name").asText());
    }

    @Test
    void toolsCallReturnsToolResult() throws Exception {
        JsonNode req =
                mapper.readTree(
                        "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"echo\",\"arguments\":{\"text\":\"hi\"}}}");
        JsonNode resp = dispatcher.handle(req);
        JsonNode content = resp.path("result").path("content");
        assertEquals("text", content.get(0).path("type").asText());
        assertEquals("hi", content.get(0).path("text").asText());
    }

    @Test
    void unknownMethodReturnsErrorObject() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"nope\"}");
        JsonNode resp = dispatcher.handle(req);
        assertTrue(resp.has("error"));
        assertEquals(-32601, resp.path("error").path("code").asInt());
    }

    @Test
    void notificationReturnsNull() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        JsonNode resp = dispatcher.handle(req);
        assertNull(resp);
    }

    @Test
    void nullRequestReturnsParseError() {
        JsonNode resp = dispatcher.handle(null);
        assertNotNull(resp);
        assertTrue(resp.has("error"));
        assertEquals(-32700, resp.path("error").path("code").asInt(),
                "JSON-RPC parse error code is -32700");
        assertTrue(resp.path("id").isNull());
    }

    @Test
    void missingMethodFieldReturnsInvalidRequest() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":5}");
        JsonNode resp = dispatcher.handle(req);
        assertTrue(resp.has("error"));
        assertEquals(-32600, resp.path("error").path("code").asInt(),
                "JSON-RPC invalid-request code is -32600");
        assertEquals(5, resp.path("id").asInt());
    }

    @Test
    void batchRequestReturnsArrayOfResponses() throws Exception {
        JsonNode batch =
                mapper.readTree(
                        "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"},"
                                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}]");
        JsonNode resp = dispatcher.handle(batch);
        assertNotNull(resp);
        assertTrue(resp.isArray());
        assertEquals(2, resp.size());
        assertEquals(1, resp.get(0).path("id").asInt());
        assertEquals(2, resp.get(1).path("id").asInt());
    }

    @Test
    void emptyBatchReturnsInvalidRequestError() throws Exception {
        // Regression: previously returned null (→ 204). Per JSON-RPC 2.0 §6 an empty
        // batch is itself an Invalid Request and the server MUST send a single error.
        JsonNode batch = mapper.readTree("[]");
        JsonNode resp = dispatcher.handle(batch);
        assertNotNull(resp);
        assertFalse(resp.isArray());
        assertTrue(resp.has("error"));
        assertEquals(-32600, resp.path("error").path("code").asInt());
        assertTrue(resp.path("id").isNull());
    }

    @Test
    void batchOfNotificationsReturnsNull() throws Exception {
        // A batch where every item is a notification should return null (no envelope).
        JsonNode batch =
                mapper.readTree(
                        "[{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"},"
                                + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}]");
        JsonNode resp = dispatcher.handle(batch);
        assertNull(resp);
    }

    @Test
    void batchMixingNotificationsAndRequestsOnlyReturnsRequestResponses() throws Exception {
        JsonNode batch =
                mapper.readTree(
                        "[{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"},"
                                + "{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"ping\"}]");
        JsonNode resp = dispatcher.handle(batch);
        assertNotNull(resp);
        assertTrue(resp.isArray());
        assertEquals(1, resp.size());
        assertEquals(42, resp.get(0).path("id").asInt());
    }

    @Test
    void toolsCallWithoutParamsObjectIsInvalidParams() throws Exception {
        JsonNode req =
                mapper.readTree(
                        "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\","
                                + "\"params\":\"not-an-object\"}");
        JsonNode resp = dispatcher.handle(req);
        assertTrue(resp.has("error"));
        // -32602 is the JSON-RPC invalid-params code.
        assertEquals(-32602, resp.path("error").path("code").asInt());
    }

    @Test
    void toolsCallWithoutNameIsInvalidParams() throws Exception {
        JsonNode req =
                mapper.readTree(
                        "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/call\","
                                + "\"params\":{\"arguments\":{}}}");
        JsonNode resp = dispatcher.handle(req);
        assertTrue(resp.has("error"));
        assertEquals(-32602, resp.path("error").path("code").asInt());
    }

    @Test
    void toolsCallWithUnknownNameIsMethodNotFound() throws Exception {
        JsonNode req =
                mapper.readTree(
                        "{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"no_such_tool\",\"arguments\":{}}}");
        JsonNode resp = dispatcher.handle(req);
        assertTrue(resp.has("error"));
        assertEquals(-32601, resp.path("error").path("code").asInt());
        assertTrue(resp.path("error").path("message").asText().contains("no_such_tool"));
    }

    @Test
    void errorResponseWithNullIdEmitsExplicitNullField() throws Exception {
        // When id is missing or null (parse error case), the JSON-RPC envelope must
        // emit `"id": null` per the spec — not omit it.
        JsonNode resp = dispatcher.handle(null);
        assertNotNull(resp);
        assertTrue(resp.has("id"));
        assertTrue(resp.path("id").isNull());
    }

    @Test
    void mcpExceptionWithDataPropagatesDataField() throws Exception {
        // Register a tool that throws an McpException carrying a data payload.
        com.fasterxml.jackson.databind.node.ObjectNode payload =
                JsonNodeFactory.instance.objectNode().put("hint", "use a smaller box");
        ToolRegistry r2 = new ToolRegistry();
        r2.register(
                new com.chapmanjw.minecraft.fabric.mcp.compat.ToolDescriptor(
                        "with_data", "throws with data", "", "", java.util.List.of(), "",
                        com.chapmanjw.minecraft.fabric.mcp.compat.ToolCategory.GAMEPLAY,
                        com.chapmanjw.minecraft.fabric.mcp.compat.ToolAccess.WRITE,
                        ThrowsWithDataTool.class),
                new ThrowsWithDataTool(payload));
        McpDispatcher d2 =
                new McpDispatcher(
                        r2,
                        new ToolContext(null, null, null, null, mapper, null, null),
                        mapper,
                        new McpDispatcher.ServerInfo("t", "0", null));
        JsonNode req =
                mapper.readTree(
                        "{\"jsonrpc\":\"2.0\",\"id\":13,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"with_data\",\"arguments\":{}}}");
        JsonNode resp = d2.handle(req);
        assertEquals("use a smaller box", resp.path("error").path("data").path("hint").asText());
    }

    @Test
    void toolResultMarkedErrorPropagatesIsErrorFlag() throws Exception {
        ToolRegistry r2 = new ToolRegistry();
        r2.register(
                new com.chapmanjw.minecraft.fabric.mcp.compat.ToolDescriptor(
                        "failer", "tool that returns isError=true", "", "",
                        java.util.List.of(), "",
                        com.chapmanjw.minecraft.fabric.mcp.compat.ToolCategory.GAMEPLAY,
                        com.chapmanjw.minecraft.fabric.mcp.compat.ToolAccess.WRITE,
                        FailerTool.class),
                new FailerTool());
        McpDispatcher d2 =
                new McpDispatcher(
                        r2,
                        new ToolContext(null, null, null, null, mapper, null, null),
                        mapper,
                        new McpDispatcher.ServerInfo("t", "0", null));
        JsonNode req =
                mapper.readTree(
                        "{\"jsonrpc\":\"2.0\",\"id\":14,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"failer\",\"arguments\":{}}}");
        JsonNode resp = d2.handle(req);
        assertTrue(resp.path("result").path("isError").asBoolean(),
                "result envelope should carry isError when ToolResult.markError() was called");
    }

    @Test
    void unhandledExceptionFromToolMapsToInternalError() throws Exception {
        // Register a tool that throws a non-McpException.
        ToolRegistry r2 = new ToolRegistry();
        r2.register(
                new com.chapmanjw.minecraft.fabric.mcp.compat.ToolDescriptor(
                        "boom", "throws", "", "", java.util.List.of(), "",
                        com.chapmanjw.minecraft.fabric.mcp.compat.ToolCategory.GAMEPLAY,
                        com.chapmanjw.minecraft.fabric.mcp.compat.ToolAccess.WRITE,
                        ExplodingTool.class),
                new ExplodingTool());
        McpDispatcher d2 =
                new McpDispatcher(
                        r2,
                        new ToolContext(null, null, null, null, mapper, null, null),
                        mapper,
                        new McpDispatcher.ServerInfo("t", "0", null));
        JsonNode req =
                mapper.readTree(
                        "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"boom\",\"arguments\":{}}}");
        JsonNode resp = d2.handle(req);
        assertTrue(resp.has("error"));
        // -32603 is the JSON-RPC internal-error code.
        assertEquals(-32603, resp.path("error").path("code").asInt());
        assertTrue(resp.path("error").path("message").asText().contains("Internal server error"));
    }

    /** Trivial tool used to verify the dispatch loop end-to-end. */
    private static final class EchoTool implements Tool {

        private static final JsonNode SCHEMA =
                new ObjectMapper().createObjectNode().put("type", "object");

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            ObjectNode args = arguments == null ? null : (ObjectNode) arguments;
            String text = args == null ? "" : args.path("text").asText("");
            return ToolResult.ofText(text);
        }
    }

    /** Tool that throws a non-McpException — exercises the generic catch path. */
    private static final class ExplodingTool implements Tool {
        private static final JsonNode SCHEMA =
                new ObjectMapper().createObjectNode().put("type", "object");

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            throw new RuntimeException("kaboom");
        }
    }

    /** Tool that throws an McpException carrying a data payload — exercises the data-passthrough path. */
    private static final class ThrowsWithDataTool implements Tool {
        private static final JsonNode SCHEMA =
                new ObjectMapper().createObjectNode().put("type", "object");
        private final JsonNode data;

        ThrowsWithDataTool(JsonNode data) {
            this.data = data;
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            throw new com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException(
                    com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes.TOOL_HANDLER_ERROR,
                    "tool said no",
                    data);
        }
    }

    /** Tool that returns a result marked as error — exercises the isError envelope passthrough. */
    private static final class FailerTool implements Tool {
        private static final JsonNode SCHEMA =
                new ObjectMapper().createObjectNode().put("type", "object");

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            return ToolResult.ofText("nope").markError();
        }
    }
}
