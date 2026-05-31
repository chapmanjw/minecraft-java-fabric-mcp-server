package com.chapmanjw.minecraft.fabric.mcp.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ToolDescriptorTest {

    @Test
    void allAccessorsReturnConstructorValues() {
        var rm = new ToolDescriptor.RequiredModule("fabric-api", ">=0.140.0");
        var td =
                new ToolDescriptor(
                        "tool-x",
                        "does x",
                        "1.21.0",
                        "26.1.99",
                        List.of(rm),
                        ">=0.16.0",
                        ToolCategory.WORLD,
                        ToolAccess.WRITE,
                        ToolDescriptorTest.class);
        assertEquals("tool-x", td.name());
        assertEquals("does x", td.description());
        assertEquals("1.21.0", td.minMinecraftVersion());
        assertEquals("26.1.99", td.maxMinecraftVersion());
        assertEquals(">=0.16.0", td.requiredFabricLoaderVersion());
        assertSame(ToolDescriptorTest.class, td.toolClass());
        assertEquals(ToolCategory.WORLD, td.category());
        assertEquals(ToolAccess.WRITE, td.access());
        assertFalse(td.readOnly(), "WRITE access is not read-only");
        assertEquals(1, td.requiredModules().size());
        assertEquals("fabric-api", td.requiredModules().get(0).moduleId());
        assertEquals(">=0.140.0", td.requiredModules().get(0).versionPredicate());
    }

    @Test
    void readOnlyConvenienceReflectsAccess() {
        var read =
                new ToolDescriptor(
                        "r", "d", "", "", List.of(), "",
                        ToolCategory.WORLD, ToolAccess.READ, ToolDescriptorTest.class);
        assertTrue(read.readOnly());
        var admin =
                new ToolDescriptor(
                        "a", "d", "", "", List.of(), "",
                        ToolCategory.SERVER, ToolAccess.ADMIN, ToolDescriptorTest.class);
        assertFalse(admin.readOnly());
        assertEquals(ToolAccess.ADMIN, admin.access());
    }

    @Test
    void requiredModulesAreImmutable() {
        java.util.ArrayList<ToolDescriptor.RequiredModule> mutable = new java.util.ArrayList<>();
        mutable.add(new ToolDescriptor.RequiredModule("a", "*"));
        ToolDescriptor td =
                new ToolDescriptor(
                        "n", "d", "", "", mutable, "",
                        ToolCategory.WORLD, ToolAccess.WRITE, ToolDescriptorTest.class);
        // Adding to the source list must not be visible in the descriptor's view.
        mutable.add(new ToolDescriptor.RequiredModule("b", "*"));
        assertEquals(1, td.requiredModules().size());
        // The returned list itself is unmodifiable.
        assertThrows(
                UnsupportedOperationException.class,
                () -> td.requiredModules().add(new ToolDescriptor.RequiredModule("c", "*")));
    }

    @Test
    void requiredModuleRecordAccessors() {
        var rm = new ToolDescriptor.RequiredModule("foo", "*");
        assertEquals("foo", rm.moduleId());
        assertEquals("*", rm.versionPredicate());
    }
}
