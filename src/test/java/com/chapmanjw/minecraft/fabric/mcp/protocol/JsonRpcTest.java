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

import org.junit.jupiter.api.Test;

class JsonRpcTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requestRoundTrip() throws Exception {
        JsonNode id = JsonNodeFactory.instance.numberNode(42);
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        params.put("a", 1);

        JsonRpc.Request req = new JsonRpc.Request("2.0", id, "tools/call", params);
        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(json.contains("\"method\":\"tools/call\""));
        assertTrue(json.contains("\"id\":42"));

        JsonRpc.Request parsed = mapper.readValue(json, JsonRpc.Request.class);
        assertEquals("2.0", parsed.jsonrpc());
        assertEquals("tools/call", parsed.method());
        assertEquals(42, parsed.id().asInt());
        assertEquals(1, parsed.params().path("a").asInt());
    }

    @Test
    void successResponseFactoryAndRoundTrip() throws Exception {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("ok", true);
        JsonNode id = JsonNodeFactory.instance.numberNode(7);

        JsonRpc.SuccessResponse r = JsonRpc.SuccessResponse.of(id, result);
        assertEquals("2.0", r.jsonrpc());
        assertEquals(7, r.id().asInt());

        String json = mapper.writeValueAsString(r);
        JsonNode tree = mapper.readTree(json);
        assertEquals("2.0", tree.path("jsonrpc").asText());
        assertEquals(7, tree.path("id").asInt());
        assertTrue(tree.path("result").path("ok").asBoolean());
        // SuccessResponse must NOT include an "error" field.
        assertFalse(tree.has("error"));
    }

    @Test
    void errorResponseFactoryAndRoundTrip() throws Exception {
        JsonNode id = JsonNodeFactory.instance.numberNode(11);
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("detail", "x");

        JsonRpc.ErrorResponse r = JsonRpc.ErrorResponse.of(id, -32600, "bad", data);
        assertEquals("2.0", r.jsonrpc());
        assertEquals(-32600, r.error().code());
        assertEquals("bad", r.error().message());
        assertEquals("x", r.error().data().path("detail").asText());

        String json = mapper.writeValueAsString(r);
        JsonNode tree = mapper.readTree(json);
        assertEquals(-32600, tree.path("error").path("code").asInt());
        assertEquals("bad", tree.path("error").path("message").asText());
        assertFalse(tree.has("result"));
    }

    @Test
    void errorBodyOmitsNullData() throws Exception {
        JsonRpc.ErrorBody body = new JsonRpc.ErrorBody(-1, "x", null);
        String json = mapper.writeValueAsString(body);
        // NON_NULL include — data should not appear.
        JsonNode tree = mapper.readTree(json);
        assertEquals(-1, tree.path("code").asInt());
        assertFalse(tree.has("data"));
    }

    @Test
    void notificationFactoryAndRoundTrip() throws Exception {
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        params.put("k", "v");
        JsonRpc.Notification n = JsonRpc.Notification.of("event/fired", params);
        assertEquals("2.0", n.jsonrpc());
        assertEquals("event/fired", n.method());
        String json = mapper.writeValueAsString(n);
        // No "id" field on notifications.
        JsonNode tree = mapper.readTree(json);
        assertFalse(tree.has("id"));
        assertEquals("event/fired", tree.path("method").asText());
        assertEquals("v", tree.path("params").path("k").asText());
    }

    @Test
    void requestWithNullIdSerializesWithoutId() throws Exception {
        JsonRpc.Request req = new JsonRpc.Request("2.0", null, "ping", null);
        String json = mapper.writeValueAsString(req);
        JsonNode tree = mapper.readTree(json);
        assertFalse(tree.has("id"));
        assertFalse(tree.has("params"));
    }

    @Test
    void errorBodyAccessorsExposeFields() {
        JsonNode data = JsonNodeFactory.instance.objectNode().put("k", 1);
        JsonRpc.ErrorBody body = new JsonRpc.ErrorBody(-32700, "parse", data);
        assertEquals(-32700, body.code());
        assertEquals("parse", body.message());
        assertNotNull(body.data());
        assertEquals(1, body.data().path("k").asInt());
        assertNull(new JsonRpc.ErrorBody(0, "", null).data());
    }
}
