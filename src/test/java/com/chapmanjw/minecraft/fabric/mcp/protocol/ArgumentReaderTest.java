package com.chapmanjw.minecraft.fabric.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;

class ArgumentReaderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ArgumentReader reader(String json) throws Exception {
        JsonNode tree = mapper.readTree(json);
        return new ArgumentReader("test_tool", tree);
    }

    @Test
    void requireStringReadsValue() throws Exception {
        ArgumentReader r = reader("{\"name\":\"alice\"}");
        assertEquals("alice", r.requireString("name"));
    }

    @Test
    void requireStringMissingThrowsWithExpectedMessage() throws Exception {
        ArgumentReader r = reader("{}");
        McpException ex = assertThrows(McpException.class, () -> r.requireString("name"));
        assertEquals(ErrorCodes.TOOL_INPUT_INVALID, ex.code());
        assertTrue(ex.getMessage().contains("test_tool"));
        assertTrue(ex.getMessage().contains("missing required argument 'name'"));
    }

    @Test
    void requireStringWrongTypeThrows() throws Exception {
        ArgumentReader r = reader("{\"name\":42}");
        McpException ex = assertThrows(McpException.class, () -> r.requireString("name"));
        assertTrue(ex.getMessage().contains("argument 'name' must be a string"));
    }

    @Test
    void requireIntAcceptsAndValidates() throws Exception {
        ArgumentReader r = reader("{\"n\":42}");
        assertEquals(42, r.requireInt("n"));
        assertThrows(McpException.class, () -> reader("{}").requireInt("n"));
        assertThrows(McpException.class, () -> reader("{\"n\":\"abc\"}").requireInt("n"));
    }

    @Test
    void requireLongAcceptsAndValidates() throws Exception {
        ArgumentReader r = reader("{\"n\":4294967296}");
        assertEquals(4294967296L, r.requireLong("n"));
        assertThrows(McpException.class, () -> reader("{}").requireLong("n"));
        assertThrows(McpException.class, () -> reader("{\"n\":\"abc\"}").requireLong("n"));
    }

    @Test
    void requireDoubleAcceptsAndValidates() throws Exception {
        assertEquals(3.14, reader("{\"d\":3.14}").requireDouble("d"), 1e-9);
        assertThrows(McpException.class, () -> reader("{}").requireDouble("d"));
        assertThrows(McpException.class, () -> reader("{\"d\":\"abc\"}").requireDouble("d"));
    }

    @Test
    void requireBooleanAcceptsAndValidates() throws Exception {
        assertTrue(reader("{\"b\":true}").requireBoolean("b"));
        assertFalse(reader("{\"b\":false}").requireBoolean("b"));
        assertThrows(McpException.class, () -> reader("{}").requireBoolean("b"));
        assertThrows(McpException.class, () -> reader("{\"b\":\"true\"}").requireBoolean("b"));
    }

    @Test
    void requireObjectAcceptsAndValidates() throws Exception {
        JsonNode obj = reader("{\"o\":{\"k\":1}}").requireObject("o");
        assertEquals(1, obj.path("k").asInt());
        assertThrows(McpException.class, () -> reader("{}").requireObject("o"));
        assertThrows(McpException.class, () -> reader("{\"o\":\"x\"}").requireObject("o"));
    }

    @Test
    void requireArrayAcceptsAndValidates() throws Exception {
        JsonNode arr = reader("{\"a\":[1,2,3]}").requireArray("a");
        assertEquals(3, arr.size());
        assertThrows(McpException.class, () -> reader("{}").requireArray("a"));
        assertThrows(McpException.class, () -> reader("{\"a\":\"x\"}").requireArray("a"));
    }

    @Test
    void nullValueIsTreatedAsMissing() throws Exception {
        // Each required reader maps {"x":null} to "missing"; this verifies the contract.
        ArgumentReader r = reader("{\"x\":null}");
        assertThrows(McpException.class, () -> r.requireString("x"));
        assertThrows(McpException.class, () -> r.requireInt("x"));
        assertThrows(McpException.class, () -> r.requireLong("x"));
        assertThrows(McpException.class, () -> r.requireDouble("x"));
        assertThrows(McpException.class, () -> r.requireBoolean("x"));
        assertThrows(McpException.class, () -> r.requireObject("x"));
        assertThrows(McpException.class, () -> r.requireArray("x"));
    }

    @Test
    void optReadersFallBackOnMissing() throws Exception {
        ArgumentReader r = reader("{}");
        assertEquals("fallback", r.optString("s", "fallback"));
        assertEquals(7, r.optInt("i", 7));
        assertEquals(7L, r.optLong("l", 7L));
        assertEquals(2.5, r.optDouble("d", 2.5), 1e-9);
        assertTrue(r.optBoolean("b", true));
        assertNull(r.optObject("o"));
        assertNull(r.optArray("a"));
        assertFalse(r.has("anything"));
    }

    @Test
    void optReadersReturnValueWhenPresent() throws Exception {
        ArgumentReader r =
                reader(
                        "{\"s\":\"x\",\"i\":3,\"l\":4,\"d\":1.5,\"b\":true,\"o\":{\"a\":1},\"a\":[1]}");
        assertEquals("x", r.optString("s", "fb"));
        assertEquals(3, r.optInt("i", 99));
        assertEquals(4L, r.optLong("l", 99L));
        assertEquals(1.5, r.optDouble("d", 99), 1e-9);
        assertTrue(r.optBoolean("b", false));
        assertNotNull(r.optObject("o"));
        assertNotNull(r.optArray("a"));
        assertTrue(r.has("s"));
    }

    @Test
    void optReadersTypeCheckEvenWhenPresent() throws Exception {
        ArgumentReader r =
                reader(
                        "{\"s\":42,\"i\":\"abc\",\"l\":\"abc\",\"d\":\"abc\","
                                + "\"b\":1,\"o\":3,\"a\":3}");
        assertThrows(McpException.class, () -> r.optString("s", null));
        assertThrows(McpException.class, () -> r.optInt("i", 0));
        assertThrows(McpException.class, () -> r.optLong("l", 0));
        assertThrows(McpException.class, () -> r.optDouble("d", 0));
        assertThrows(McpException.class, () -> r.optBoolean("b", false));
        assertThrows(McpException.class, () -> r.optObject("o"));
        assertThrows(McpException.class, () -> r.optArray("a"));
    }

    @Test
    void hasReturnsFalseForNullOrMissing() throws Exception {
        ArgumentReader r = reader("{\"a\":1,\"b\":null}");
        assertTrue(r.has("a"));
        assertFalse(r.has("b"));
        assertFalse(r.has("c"));
    }

    @Test
    void rawReturnsRootNode() throws Exception {
        ArgumentReader r = reader("{\"a\":1}");
        assertNotNull(r.raw());
        assertEquals(1, r.raw().path("a").asInt());
    }

    @Test
    void nullRootBecomesEmptyObject() {
        // The constructor coerces a null root into an empty object so that callers
        // can still ask for optional fields safely.
        ArgumentReader r = new ArgumentReader("tool", null);
        assertNotNull(r.raw());
        assertFalse(r.has("anything"));
        assertEquals("fb", r.optString("x", "fb"));
    }

    @Test
    void mismatchErrorIncludesExpectedType() throws Exception {
        ArgumentReader r = reader("{\"x\":1.5}");
        McpException ex = assertThrows(McpException.class, () -> r.requireString("x"));
        assertTrue(ex.getMessage().contains("must be a string"), ex.getMessage());
        assertNull(ex.data());
    }

    @Test
    void differingFloatRejectedForRequireInt() throws Exception {
        // Jackson's canConvertToInt rejects 1.5 because it isn't exactly representable as int.
        ArgumentReader r = reader("{\"x\":1.5}");
        assertThrows(McpException.class, () -> r.requireInt("x"));
    }

    @Test
    void integerCanBeReadAsDouble() throws Exception {
        ArgumentReader r = reader("{\"x\":42}");
        assertEquals(42.0, r.requireDouble("x"), 1e-9);
    }

    @Test
    void objectNodeArgumentReaderIsBuiltFromTree() {
        ObjectNode tree = JsonNodeFactory.instance.objectNode();
        tree.put("k", "v");
        ArgumentReader r = new ArgumentReader("t", tree);
        assertEquals("v", r.requireString("k"));
    }
}
