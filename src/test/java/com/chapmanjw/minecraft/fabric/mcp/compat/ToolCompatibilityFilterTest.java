package com.chapmanjw.minecraft.fabric.mcp.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chapmanjw.minecraft.fabric.mcp.config.Config;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

class ToolCompatibilityFilterTest {

    private static McEnvironment env() {
        // 1.21.11 with Fabric Loader 0.16.10 and one Fabric API module installed.
        return new McEnvironment(
                "1.21.11",
                "0.16.10",
                Map.of(
                        "fabric-api", "0.141.4+1.21.11",
                        "fabricloader", "0.16.10"));
    }

    private static Config baseConfig() {
        return Config.defaults();
    }

    private static Config configWith(
            List<String> included, List<String> excluded, boolean excludeWrites) {
        Config d = Config.defaults();
        return new Config(
                d.host(),
                d.port(),
                d.authRequired(),
                d.bearerToken(),
                d.allowRemote(),
                d.allowedOrigins(),
                d.commandTimeoutMs(),
                d.rateLimitRpm(),
                d.maxBodyBytes(),
                d.eventBufferSize(),
                d.queueMax(),
                d.logLevel(),
                d.tlsCertPath(),
                d.tlsKeyPath(),
                d.metricsEnabled(),
                included,
                excluded,
                excludeWrites);
    }

    // Test fixtures use real domain prefixes so ToolCategory.forToolName can resolve
    // them. Each name is constructed to fit a real category bucket — see ToolCategory.
    @McpTool(name = "level_test_free", description = "No constraints")
    static class FreeTool {}

    @McpTool(
            name = "level_test_future",
            description = "Needs newer MC",
            minMinecraftVersion = "26.0.0")
    static class FutureMcTool {}

    @McpTool(
            name = "level_test_legacy",
            description = "Old MC only",
            maxMinecraftVersion = "1.20.0")
    static class LegacyMcTool {}

    @McpTool(
            name = "level_test_missing_mod",
            description = "Needs an absent module",
            requiredFabricModules = {"fabric-not-installed"})
    static class MissingModTool {}

    @McpTool(
            name = "level_test_wrong_version",
            description = "Needs a newer fabric-api",
            requiredFabricModules = {"fabric-api"},
            requiredModuleVersions = {">=99.0.0"})
    static class WrongVersionTool {}

    @McpTool(
            name = "level_test_good_mod",
            description = "Matches present module",
            requiredFabricModules = {"fabric-api"},
            requiredModuleVersions = {">=0.140.0"})
    static class GoodModTool {}

    @McpTool(
            name = "level_test_bad_loader",
            description = "Bad loader range",
            requiredFabricLoaderVersion = "garbage(((")
    static class BadLoaderRangeTool {}

    @McpTool(
            name = "level_test_good_loader",
            description = "Loader range matches",
            requiredFabricLoaderVersion = ">=0.16.0")
    static class GoodLoaderRangeTool {}

    // For category + readOnly tests. WORLD-category, write-only (no read-verb fragment).
    @McpTool(name = "level_test_write", description = "Writes state")
    static class LevelWriteTool {}

    // WORLD-category, read-only by heuristic (contains _get_).
    @McpTool(name = "level_get_test", description = "Reads state")
    static class LevelReadTool {}

    // ACTORS-category, write-only — used to test category include/exclude.
    @McpTool(name = "player_test_write", description = "Player write")
    static class PlayerWriteTool {}

    // Annotation-only read-only (no read-verb fragment in name).
    @McpTool(name = "level_test_inspect", description = "Inspect", readOnly = true)
    static class AnnotationOnlyReadOnlyTool {}

    static class Unannotated {}

    @Test
    void unconstrainedToolAlwaysCompatible() {
        Optional<ToolDescriptor> d = new ToolCompatibilityFilter(env()).evaluate(FreeTool.class);
        assertTrue(d.isPresent());
        assertEquals("level_test_free", d.get().name());
        assertSame(FreeTool.class, d.get().toolClass());
        assertEquals(ToolCategory.WORLD, d.get().category());
        assertFalse(d.get().readOnly());
    }

    @Test
    void minMinecraftVersionAboveEnvFails() {
        assertTrue(new ToolCompatibilityFilter(env()).evaluate(FutureMcTool.class).isEmpty());
    }

    @Test
    void maxMinecraftVersionBelowEnvFails() {
        assertTrue(new ToolCompatibilityFilter(env()).evaluate(LegacyMcTool.class).isEmpty());
    }

    @Test
    void missingRequiredModuleFails() {
        assertTrue(new ToolCompatibilityFilter(env()).evaluate(MissingModTool.class).isEmpty());
    }

    @Test
    void presentModuleButFailingPredicateFails() {
        assertTrue(new ToolCompatibilityFilter(env()).evaluate(WrongVersionTool.class).isEmpty());
    }

    @Test
    void presentModuleWithPassingPredicateCompatible() {
        Optional<ToolDescriptor> d =
                new ToolCompatibilityFilter(env()).evaluate(GoodModTool.class);
        assertTrue(d.isPresent());
        assertEquals(1, d.get().requiredModules().size());
        assertEquals("fabric-api", d.get().requiredModules().get(0).moduleId());
        assertEquals(">=0.140.0", d.get().requiredModules().get(0).versionPredicate());
    }

    @Test
    void unparseableFabricLoaderRangeFails() {
        assertTrue(new ToolCompatibilityFilter(env()).evaluate(BadLoaderRangeTool.class).isEmpty());
    }

    @Test
    void passingFabricLoaderRangeAccepts() {
        assertTrue(new ToolCompatibilityFilter(env()).evaluate(GoodLoaderRangeTool.class).isPresent());
    }

    @Test
    void missingAnnotationThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolCompatibilityFilter(env()).evaluate(Unannotated.class));
    }

    @Test
    void heuristicMarksReadVerbAsReadOnly() {
        Optional<ToolDescriptor> d =
                new ToolCompatibilityFilter(env()).evaluate(LevelReadTool.class);
        assertTrue(d.isPresent());
        assertTrue(d.get().readOnly(), "Tool with _get_ in name should be flagged read-only");
    }

    @Test
    void annotationCanOverrideHeuristicToReadOnly() {
        Optional<ToolDescriptor> d =
                new ToolCompatibilityFilter(env()).evaluate(AnnotationOnlyReadOnlyTool.class);
        assertTrue(d.isPresent());
        assertTrue(d.get().readOnly(), "readOnly=true annotation should force read-only");
    }

    @Test
    void excludeWriteToolsDropsMutators() {
        Config cfg = configWith(List.of(), List.of(), true);
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env(), cfg);
        assertTrue(filter.evaluate(LevelWriteTool.class).isEmpty(),
                "Write tool should be dropped when excludeWriteTools=true");
        assertTrue(filter.evaluate(LevelReadTool.class).isPresent(),
                "Read tool should survive excludeWriteTools=true");
        assertTrue(filter.evaluate(AnnotationOnlyReadOnlyTool.class).isPresent(),
                "Annotation-readOnly tool should survive excludeWriteTools=true");
    }

    @Test
    void includedCategoriesAllowsOnlyMatchingCategories() {
        Config cfg = configWith(List.of("world"), List.of(), false);
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env(), cfg);
        assertTrue(filter.evaluate(LevelWriteTool.class).isPresent(),
                "WORLD-category tool should pass when included=[world]");
        assertTrue(filter.evaluate(PlayerWriteTool.class).isEmpty(),
                "ACTORS-category tool should be dropped when included=[world]");
    }

    @Test
    void excludedCategoriesRejectsMatchingCategories() {
        Config cfg = configWith(List.of(), List.of("actors"), false);
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env(), cfg);
        assertTrue(filter.evaluate(LevelWriteTool.class).isPresent(),
                "WORLD tool should pass when only actors excluded");
        assertTrue(filter.evaluate(PlayerWriteTool.class).isEmpty(),
                "ACTORS tool should be dropped when actors excluded");
    }

    @Test
    void unknownCategoryNamesAreIgnored() {
        // Garbage category names should not crash boot — filter should accept everything.
        Config cfg = configWith(List.of("garbage"), List.of("nope"), false);
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env(), cfg);
        // includedCategories is non-empty in the user's view, but every entry is invalid →
        // effective set is empty → no inclusion filter applies → tool passes.
        assertTrue(filter.evaluate(LevelWriteTool.class).isPresent());
        assertTrue(filter.evaluate(PlayerWriteTool.class).isPresent());
    }

    @Test
    void includeAndExcludeCanCombine() {
        Config cfg = configWith(List.of("world", "actors"), List.of("actors"), false);
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env(), cfg);
        assertTrue(filter.evaluate(LevelWriteTool.class).isPresent(),
                "WORLD passes: in includes, not in excludes");
        assertTrue(filter.evaluate(PlayerWriteTool.class).isEmpty(),
                "ACTORS dropped: present in excludes despite includes");
    }
}
