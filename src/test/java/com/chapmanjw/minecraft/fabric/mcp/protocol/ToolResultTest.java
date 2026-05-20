package com.chapmanjw.minecraft.fabric.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

class ToolResultTest {

    @Test
    void createIsEmpty() {
        ToolResult r = ToolResult.create();
        assertTrue(r.content().isEmpty());
        assertFalse(r.isError());
    }

    @Test
    void addTextAppendsContent() {
        ToolResult r = ToolResult.create().addText("hello");
        assertEquals(1, r.content().size());
        assertEquals("text", r.content().get(0).path("type").asText());
        assertEquals("hello", r.content().get(0).path("text").asText());
    }

    @Test
    void addTextNullBecomesEmptyString() {
        ToolResult r = ToolResult.create().addText(null);
        assertEquals(1, r.content().size());
        assertEquals("", r.content().get(0).path("text").asText());
    }

    @Test
    void addTextMultipleAppendsInOrder() {
        ToolResult r = ToolResult.create().addText("a").addText("b").addText("c");
        assertEquals(3, r.content().size());
        assertEquals("a", r.content().get(0).path("text").asText());
        assertEquals("b", r.content().get(1).path("text").asText());
        assertEquals("c", r.content().get(2).path("text").asText());
    }

    @Test
    void markErrorSetsFlag() {
        ToolResult r = ToolResult.create().addText("boom").markError();
        assertTrue(r.isError());
    }

    @Test
    void ofTextShortcut() {
        ToolResult r = ToolResult.ofText("hi");
        assertEquals(1, r.content().size());
        assertEquals("hi", r.content().get(0).path("text").asText());
        assertFalse(r.isError());
    }

    @Test
    void ofToonEncodesPayloadAsToonText() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("count", 5);
        payload.put("name", "Ada");
        ToolResult r = ToolResult.ofToon(payload);
        assertEquals(1, r.content().size());
        assertEquals("text", r.content().get(0).path("type").asText());
        // TOON encodes the object's fields as key: value lines with no braces.
        assertEquals("count: 5\nname: Ada", r.content().get(0).path("text").asText());
        assertFalse(r.isError());
    }
}
