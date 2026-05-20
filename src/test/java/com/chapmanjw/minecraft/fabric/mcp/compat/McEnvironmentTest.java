package com.chapmanjw.minecraft.fabric.mcp.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link McEnvironment}. Only the pure record methods are exercised here —
 * the static {@code capture()} factory reaches into FabricLoader, which is only present
 * in a running Minecraft mod environment and would NoClassDefFoundError under plain JUnit.
 */
class McEnvironmentTest {

    @Test
    void moduleVersionReturnsPresentValue() {
        McEnvironment env =
                new McEnvironment(
                        "1.21.11",
                        "0.16.10",
                        Map.of("fabric-api", "0.141.4+1.21.11", "fabric-loader", "0.16.10"));
        assertEquals("0.141.4+1.21.11", env.moduleVersion("fabric-api").orElseThrow());
        assertEquals("0.16.10", env.moduleVersion("fabric-loader").orElseThrow());
    }

    @Test
    void moduleVersionReturnsEmptyForUnknown() {
        McEnvironment env =
                new McEnvironment("1.21.11", "0.16.10", Map.of("only-this", "1.0.0"));
        assertTrue(env.moduleVersion("missing").isEmpty());
    }

    @Test
    void hasModuleReflectsMembership() {
        McEnvironment env =
                new McEnvironment(
                        "1.21.11", "0.16.10", Map.of("a", "1.0.0", "b", "2.0.0"));
        assertTrue(env.hasModule("a"));
        assertTrue(env.hasModule("b"));
        assertFalse(env.hasModule("c"));
    }

    @Test
    void recordAccessorsReturnConstructorValues() {
        Map<String, String> mods = Map.of("x", "1.0.0");
        McEnvironment env = new McEnvironment("26.1.2", "0.18.0", mods);
        assertEquals("26.1.2", env.minecraftVersion());
        assertEquals("0.18.0", env.fabricLoaderVersion());
        assertEquals(mods, env.loadedMods());
    }

    @Test
    void loadedModsIsDefensivelyCopied() {
        // The compact constructor must produce an immutable copy so the caller can't
        // mutate the environment by holding a reference to the original map.
        java.util.HashMap<String, String> mods = new java.util.HashMap<>();
        mods.put("foo", "1.0.0");
        McEnvironment env = new McEnvironment("1.21.11", "0.16.10", mods);
        mods.put("bar", "2.0.0");
        assertFalse(env.hasModule("bar"), "post-construction mutation must not leak in");
    }
}
