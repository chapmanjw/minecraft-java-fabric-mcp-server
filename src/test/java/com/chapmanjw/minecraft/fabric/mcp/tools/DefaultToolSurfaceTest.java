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
                        // Every entry here must be a module Fabric API actually ships. This map
                        // previously listed fabric-resource-loader-v0 and
                        // fabric-screen-handler-api-v1, neither of which exists (they are v1 and
                        // fabric-menu-api-v1 respectively), which masked five tools that declared
                        // them and were therefore filtered out at runtime on every version.
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

    /**
     * Regression guard for tools that were silently filtered out at runtime by
     * {@code requiredFabricModules} entries naming modules Fabric API does not ship
     * (fabric-screen-handler-api-v1, fabric-resource-loader-v0). They registered fine in this test
     * only because {@link #fullModuleEnv()} used to fabricate those modules, so the outage was
     * invisible here for as long as it existed.
     *
     * <p>This asserts against an environment built from the modules Fabric API ACTUALLY ships, so a
     * future bogus module requirement fails the suite instead of silently dropping a tool.
     */
    @Test
    void everyDeclaredFabricModuleIsOneFabricApiActuallyShips() {
        // The modules Fabric API really ships, as listed in its POM. Notably ABSENT and therefore
        // never valid to require: fabric-screen-handler-api-v1 (it is fabric-menu-api-v1) and
        // fabric-resource-loader-v0 (it is v1). Five tools required exactly those two and were
        // filtered out at runtime on every Minecraft version as a result.
        Set<String> shipped =
                Set.of(
                        "fabric-api-base", "fabric-api-lookup-api-v1", "fabric-biome-api-v1",
                        "fabric-block-api-v1", "fabric-block-getter-api-v2", "fabric-command-api-v2",
                        "fabric-content-registries-v0", "fabric-convention-tags-v2",
                        "fabric-crash-report-info-v1", "fabric-creative-tab-api-v1",
                        "fabric-data-attachment-api-v1", "fabric-data-generation-api-v1",
                        "fabric-debug-api-v1", "fabric-dimensions-v1", "fabric-entity-events-v1",
                        "fabric-events-interaction-v0", "fabric-game-rule-api-v1",
                        "fabric-gametest-api-v1", "fabric-item-api-v1", "fabric-key-mapping-api-v1",
                        "fabric-lifecycle-events-v1", "fabric-loot-api-v3", "fabric-menu-api-v1",
                        "fabric-message-api-v1", "fabric-model-loading-api-v1",
                        "fabric-networking-api-v1", "fabric-object-builder-api-v1",
                        "fabric-particles-v1", "fabric-permission-api-v1", "fabric-recipe-api-v1",
                        "fabric-registry-sync-v0", "fabric-renderer-api-v1",
                        "fabric-rendering-fluids-v1", "fabric-rendering-v1",
                        "fabric-resource-conditions-api-v1", "fabric-resource-loader-v1",
                        "fabric-screen-api-v1", "fabric-serialization-api-v1",
                        "fabric-sound-api-v1", "fabric-tag-api-v1", "fabric-transfer-api-v1",
                        "fabric-transitive-access-wideners-v1");

        for (Class<? extends Tool> klass : ToolRegistration.ALL_TOOL_CLASSES) {
            var meta =
                    klass.getAnnotation(
                            com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool.class);
            if (meta == null) {
                continue;
            }
            for (String module : meta.requiredFabricModules()) {
                assertTrue(
                        shipped.contains(module),
                        klass.getSimpleName()
                                + " ('"
                                + meta.name()
                                + "') requires Fabric module '"
                                + module
                                + "', which Fabric API does not ship. A required module that is not"
                                + " installed causes the tool to be skipped at runtime, so this"
                                + " would silently disable the tool on every Minecraft version.");
            }
        }
    }

    @Test
    void blockFamilyVariantsIsInTheDefaultSurface() {
        ToolCompatibilityFilter filter =
                new ToolCompatibilityFilter(fullModuleEnv(), Config.defaults());
        Set<String> survivors = new HashSet<>();
        for (Class<? extends Tool> klass : ToolRegistration.ALL_TOOL_CLASSES) {
            filter.evaluate(klass).ifPresent(d -> survivors.add(d.name()));
        }
        assertTrue(
                survivors.contains("block_family_variants"),
                "block_family_variants should be in the default surface");
        assertTrue(
                survivors.contains("level_poi_query"),
                "level_poi_query should be in the default surface");
    }
}
