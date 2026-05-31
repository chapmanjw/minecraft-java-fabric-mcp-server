package com.chapmanjw.minecraft.fabric.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.chapmanjw.minecraft.fabric.mcp.compat.McEnvironment;
import com.chapmanjw.minecraft.fabric.mcp.compat.ToolCategory;
import com.chapmanjw.minecraft.fabric.mcp.compat.ToolCompatibilityFilter;
import com.chapmanjw.minecraft.fabric.mcp.compat.ToolDescriptor;
import com.chapmanjw.minecraft.fabric.mcp.config.Config;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Tool;

/**
 * Pins the default tool surface: with {@link Config#defaults()} (no config), only the
 * default-on category tools whose access is read or write register — roughly 102 of the
 * full universe. This test evaluates every entry in {@link ToolRegistration#ALL_TOOL_CLASSES}
 * through the default filter and asserts both the survivor count and an exact membership
 * spot-check so the lean default can't silently drift.
 */
class DefaultToolSurfaceTest {

    /**
     * A permissive environment where every Fabric API module a tool can require is
     * present (any version satisfies — no tool pins a module version), and the MC
     * version is the build target, so version/module gates never interfere. What
     * remains is purely the category + access filtering.
     */
    private static McEnvironment fullModuleEnv() {
        Map<String, String> modules =
                Map.ofEntries(
                        Map.entry("fabric-api", "1.0.0"),
                        Map.entry("fabric-loader", "0.16.10"),
                        Map.entry("fabric-biome-api-v1", "1.0.0"),
                        Map.entry("fabric-command-api-v2", "1.0.0"),
                        Map.entry("fabric-content-registries-v0", "1.0.0"),
                        Map.entry("fabric-convention-tags-v2", "1.0.0"),
                        Map.entry("fabric-data-attachment-api-v1", "1.0.0"),
                        Map.entry("fabric-events-interaction-v0", "1.0.0"),
                        Map.entry("fabric-game-rule-api-v1", "1.0.0"),
                        Map.entry("fabric-lifecycle-events-v1", "1.0.0"),
                        Map.entry("fabric-loot-api-v3", "1.0.0"),
                        Map.entry("fabric-message-api-v1", "1.0.0"),
                        Map.entry("fabric-networking-api-v1", "1.0.0"),
                        Map.entry("fabric-recipe-api-v1", "1.0.0"),
                        Map.entry("fabric-resource-conditions-api-v1", "1.0.0"),
                        Map.entry("fabric-resource-loader-v0", "1.0.0"),
                        Map.entry("fabric-screen-handler-api-v1", "1.0.0"),
                        Map.entry("fabric-transfer-api-v1", "1.0.0"));
        return new McEnvironment("26.1.2", "0.16.10", modules);
    }

    @Test
    void defaultSurfaceCountAndMembership() {
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(fullModuleEnv(), Config.defaults());

        EnumMap<ToolCategory, Integer> perCategory = new EnumMap<>(ToolCategory.class);
        Set<String> survivors = new HashSet<>();
        for (Class<? extends Tool> klass : ToolRegistration.ALL_TOOL_CLASSES) {
            Optional<ToolDescriptor> d = filter.evaluate(klass);
            if (d.isPresent()) {
                survivors.add(d.get().name());
                perCategory.merge(d.get().category(), 1, Integer::sum);
            }
        }

        // Print the per-category survivor breakdown (sorted by wire name for stable logs).
        TreeMap<String, Integer> sorted = new TreeMap<>();
        for (var e : perCategory.entrySet()) {
            sorted.put(e.getKey().wireName(), e.getValue());
        }
        System.out.println("Default tool surface: " + survivors.size() + " tools");
        sorted.forEach((cat, n) -> System.out.println("  " + cat + " = " + n));

        int count = survivors.size();
        assertTrue(
                count >= 98 && count <= 106,
                "Default surface should be ~102 tools, got " + count + " " + sorted);

        // Exact membership spot-check: default-on read/write tools IN, opt-in + admin OUT.
        assertTrue(survivors.contains("block_set_state"), "block_set_state should be IN");
        assertTrue(survivors.contains("level_set_time"), "level_set_time should be IN");
        assertTrue(survivors.contains("command_execute"), "command_execute should be IN");
        assertTrue(survivors.contains("server_get_status"), "server_get_status should be IN");

        assertFalse(survivors.contains("level_set_difficulty"), "level_set_difficulty (admin) should be OUT");
        assertFalse(survivors.contains("command_register"), "command_register (admin) should be OUT");
        assertFalse(survivors.contains("datapack_enable"), "datapack_enable (admin) should be OUT");
        assertFalse(survivors.contains("scoreboard_add_objective"), "scoreboard_add_objective (gameplay) should be OUT");
        assertFalse(survivors.contains("player_give_item"), "player_give_item (players) should be OUT");
        assertFalse(survivors.contains("recipe_list"), "recipe_list (registries) should be OUT");
    }
}
