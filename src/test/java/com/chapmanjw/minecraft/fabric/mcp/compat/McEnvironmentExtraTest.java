package com.chapmanjw.minecraft.fabric.mcp.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Direct-construction tests for {@link McEnvironment}. The {@code capture()} factory
 * uses FabricLoader and can only run inside a Minecraft launch (covered by gametests);
 * the rest of the API is straight value-object territory.
 */
class McEnvironmentExtraTest {

    @Test
    void moduleVersionReturnsValueWhenPresent() {
        var env =
                new McEnvironment(
                        "1.21.11",
                        "0.19.2",
                        Map.of(
                                "fabric-api", "0.141.4+1.21.11",
                                "fabric-biome-api-v1", "14.0.1"));
        assertEquals("0.141.4+1.21.11", env.moduleVersion("fabric-api").orElseThrow());
        assertEquals("14.0.1", env.moduleVersion("fabric-biome-api-v1").orElseThrow());
    }

    @Test
    void moduleVersionReturnsEmptyWhenAbsent() {
        var env = new McEnvironment("1.21.11", "0.19.2", Map.of());
        assertTrue(env.moduleVersion("fabric-not-installed").isEmpty());
    }

    @Test
    void hasModuleReflectsLoadedSet() {
        var env =
                new McEnvironment(
                        "1.21.11",
                        "0.19.2",
                        Map.of("fabric-loot-api-v3", "3.0.0"));
        assertTrue(env.hasModule("fabric-loot-api-v3"));
        assertFalse(env.hasModule("nope"));
    }

    @Test
    void minecraftAndLoaderAccessorsReturnConstructorValues() {
        var env = new McEnvironment("26.1.2", "0.19.2", Map.of());
        assertEquals("26.1.2", env.minecraftVersion());
        assertEquals("0.19.2", env.fabricLoaderVersion());
    }

    @Test
    void loadedModsMapIsImmutable() {
        var env = new McEnvironment("1.21.11", "0.19.2", Map.of("a", "1"));
        try {
            env.loadedMods().put("hacked", "yes");
            // If it didn't throw, the record's copy was not immutable.
            assertTrue(false, "loadedMods() should return an immutable view");
        } catch (UnsupportedOperationException expected) {
            // Good — record's compact constructor uses Map.copyOf.
        }
    }
}
