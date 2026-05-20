package com.chapmanjw.minecraft.fabric.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import org.junit.jupiter.api.Test;

import com.chapmanjw.minecraft.fabric.mcp.compat.ToolCategory;
import com.chapmanjw.minecraft.fabric.mcp.compat.ToolDescriptor;

class ToolRegistryTest {

    private static ToolDescriptor descriptor(String name) {
        return new ToolDescriptor(
                name, "desc", "", "", List.of(), "",
                ToolCategory.WORLD, false, ToolRegistryTest.class);
    }

    private static final class NoopTool implements Tool {
        @Override
        public JsonNode inputSchema() {
            return JsonNodeFactory.instance.objectNode().put("type", "object");
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            return ToolResult.ofText("ok");
        }
    }

    @Test
    void registerAndLookup() {
        ToolRegistry reg = new ToolRegistry();
        NoopTool tool = new NoopTool();
        ToolDescriptor d = descriptor("a");
        reg.register(d, tool);
        var entry = reg.lookup("a").orElseThrow();
        assertSame(d, entry.descriptor());
        assertSame(tool, entry.tool());
    }

    @Test
    void lookupMissingReturnsEmpty() {
        ToolRegistry reg = new ToolRegistry();
        assertTrue(reg.lookup("nope").isEmpty());
    }

    @Test
    void listReturnsAllRegistered() {
        ToolRegistry reg = new ToolRegistry();
        reg.register(descriptor("a"), new NoopTool());
        reg.register(descriptor("b"), new NoopTool());
        reg.register(descriptor("c"), new NoopTool());
        assertEquals(3, reg.list().size());
    }

    @Test
    void sizeReflectsRegistration() {
        ToolRegistry reg = new ToolRegistry();
        assertEquals(0, reg.size());
        reg.register(descriptor("a"), new NoopTool());
        assertEquals(1, reg.size());
    }

    @Test
    void duplicateNameThrows() {
        ToolRegistry reg = new ToolRegistry();
        reg.register(descriptor("dup"), new NoopTool());
        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> reg.register(descriptor("dup"), new NoopTool()));
        assertTrue(ex.getMessage().contains("dup"));
    }

    @Test
    void entryRecordAccessors() {
        var d = descriptor("e");
        var t = new NoopTool();
        ToolRegistry.Entry entry = new ToolRegistry.Entry(d, t);
        assertSame(d, entry.descriptor());
        assertSame(t, entry.tool());
    }
}
