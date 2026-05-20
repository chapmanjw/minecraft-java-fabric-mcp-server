package com.chapmanjw.minecraft.fabric.mcp.protocol.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import org.junit.jupiter.api.Test;

class McpExceptionTest {

    @Test
    void codeAndMessageOnly() {
        McpException ex = new McpException(-32600, "Invalid Request");
        assertEquals(-32600, ex.code());
        assertEquals("Invalid Request", ex.getMessage());
        assertNull(ex.data());
        assertNull(ex.getCause());
    }

    @Test
    void codeMessageData() {
        JsonNode data = JsonNodeFactory.instance.objectNode().put("k", "v");
        McpException ex = new McpException(-1, "msg", data);
        assertEquals(-1, ex.code());
        assertEquals("msg", ex.getMessage());
        assertSame(data, ex.data());
        assertNull(ex.getCause());
    }

    @Test
    void codeMessageDataNullData() {
        // Verifies the (int, String, JsonNode) constructor accepts null for data.
        McpException ex = new McpException(-2, "no data", (JsonNode) null);
        assertEquals(-2, ex.code());
        assertNull(ex.data());
    }

    @Test
    void codeMessageCause() {
        Throwable cause = new IllegalStateException("inner");
        McpException ex = new McpException(-32603, "outer", cause);
        assertEquals(-32603, ex.code());
        assertEquals("outer", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertNull(ex.data());
    }

    @Test
    void isRuntimeException() {
        assertNotNull(new McpException(0, "x"));
        assertEquals(RuntimeException.class, McpException.class.getSuperclass());
    }
}
