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
            List<String> included, List<String> excluded, String maxAccess, boolean excludeWrites) {
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
                maxAccess,
                excludeWrites);
    }

    // --- version / module fixtures (WORLD-category, default-on, so the category gate
    //     never interferes with these constraint checks) ----------------------------

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

    // WORLD-category, write-only (no read-verb fragment).
    @McpTool(name = "level_test_write", description = "Writes state")
    static class LevelWriteTool {}

    // WORLD-category, read-only by heuristic (contains _get_).
    @McpTool(name = "level_get_test", description = "Reads state")
    static class LevelReadTool {}

    // Annotation-only read-only (no read-verb fragment in name).
    @McpTool(name = "level_test_inspect", description = "Inspect", readOnly = true)
    static class AnnotationOnlyReadOnlyTool {}

    // Admin tool in a default-on domain.
    @McpTool(name = "level_test_admin", description = "Admin op", admin = true)
    static class LevelAdminTool {}

    // GAMEPLAY-category (opt-in), write.
    @McpTool(name = "scoreboard_test_write", description = "Gameplay write")
    static class GameplayWriteTool {}

    static class Unannotated {}

    @Test
    void unconstrainedToolAlwaysCompatible() {
        Optional<ToolDescriptor> d = new ToolCompatibilityFilter(env()).evaluate(FreeTool.class);
        assertTrue(d.isPresent());
        assertEquals("level_test_free", d.get().name());
        assertSame(FreeTool.class, d.get().toolClass());
        assertEquals(ToolCategory.WORLD, d.get().category());
        assertEquals(ToolAccess.WRITE, d.get().access());
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
        assertEquals(ToolAccess.READ, d.get().access());
        assertTrue(d.get().readOnly(), "Tool with _get_ in name should be flagged read-only");
    }

    @Test
    void annotationCanOverrideHeuristicToReadOnly() {
        Optional<ToolDescriptor> d =
                new ToolCompatibilityFilter(env()).evaluate(AnnotationOnlyReadOnlyTool.class);
        assertTrue(d.isPresent());
        assertEquals(ToolAccess.READ, d.get().access());
    }

    @Test
    void adminFlagSetsAdminAccess() {
        // With max_access=admin the admin tool registers and carries ADMIN access.
        Config cfg = configWith(List.of(), List.of(), "admin", false);
        Optional<ToolDescriptor> d =
                new ToolCompatibilityFilter(env(), cfg).evaluate(LevelAdminTool.class);
        assertTrue(d.isPresent());
        assertEquals(ToolAccess.ADMIN, d.get().access());
    }

    // --- defaults: ON read/write accepted ------------------------------------------

    @Test
    void defaultsAcceptDefaultOnReadAndWriteTools() {
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env(), baseConfig());
        assertTrue(filter.evaluate(LevelWriteTool.class).isPresent(),
                "WORLD write tool should register under defaults");
        assertTrue(filter.evaluate(LevelReadTool.class).isPresent(),
                "WORLD read tool should register under defaults");
        assertTrue(filter.evaluate(FreeTool.class).isPresent());
    }

    // --- defaults: opt-in domains rejected -----------------------------------------

    @Test
    void defaultsRejectOptInDomainTools() {
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env(), baseConfig());
        assertTrue(filter.evaluate(GameplayWriteTool.class).isEmpty(),
                "GAMEPLAY is opt-in; should be dropped under defaults");
    }

    // --- defaults: admin tools in ON domains rejected ------------------------------

    @Test
    void defaultsRejectAdminToolsInOnDomains() {
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env(), baseConfig());
        assertTrue(filter.evaluate(LevelAdminTool.class).isEmpty(),
                "Admin tool exceeds default max_access=write; should be dropped");
    }

    // --- includedCategories allowlist ----------------------------------------------

    @Test
    void includedCategoriesGameplayRegistersOnlyGameplay() {
        Config cfg = configWith(List.of("gameplay"), List.of(), "write", false);
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env(), cfg);
        assertTrue(filter.evaluate(GameplayWriteTool.class).isPresent(),
                "GAMEPLAY tool should register when included=[gameplay]");
        assertTrue(filter.evaluate(LevelWriteTool.class).isEmpty(),
                "WORLD tool should be dropped when included=[gameplay]");
    }

    @Test
    void excludedCategoriesSubtractFromDefaultOn() {
        Config cfg = configWith(List.of(), List.of("world"), "write", false);
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env(), cfg);
        assertTrue(filter.evaluate(LevelWriteTool.class).isEmpty(),
                "WORLD excluded → dropped even though default-on");
        // A different default-on domain (blocks) still survives — sanity via FreeTool is
        // WORLD too, so use the read tool which is also WORLD; instead confirm exclusion only
        // affects the named category by re-including via includes.
    }

    // --- maxAccess raises the cap --------------------------------------------------

    @Test
    void maxAccessAdminRegistersAdminTools() {
        Config cfg = configWith(List.of(), List.of(), "admin", false);
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env(), cfg);
        assertTrue(filter.evaluate(LevelAdminTool.class).isPresent(),
                "Admin tool should register when max_access=admin");
        // Write + read still register.
        assertTrue(filter.evaluate(LevelWriteTool.class).isPresent());
        assertTrue(filter.evaluate(LevelReadTool.class).isPresent());
    }

    @Test
    void maxAccessReadDropsWriteTools() {
        Config cfg = configWith(List.of(), List.of(), "read", false);
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env(), cfg);
        assertTrue(filter.evaluate(LevelWriteTool.class).isEmpty(),
                "Write tool should be dropped when max_access=read");
        assertTrue(filter.evaluate(LevelReadTool.class).isPresent(),
                "Read tool survives max_access=read");
        assertTrue(filter.evaluate(LevelAdminTool.class).isEmpty(),
                "Admin tool dropped when max_access=read");
    }

    // --- legacy excludeWriteTools == max_access=read -------------------------------

    @Test
    void legacyExcludeWriteToolsLowersCapToRead() {
        Config cfg = configWith(List.of(), List.of(), "write", true);
        ToolCompatibilityFilter filter = new ToolCompatibilityFilter(env(), cfg);
        assertTrue(filter.evaluate(LevelWriteTool.class).isEmpty(),
                "excludeWriteTools=true behaves like max_access=read");
        assertTrue(filter.evaluate(LevelReadTool.class).isPresent());
        assertTrue(filter.evaluate(AnnotationOnlyReadOnlyTool.class).isPresent());
    }

    @Test
    void effectiveAccessHelperMatchesAnnotation() throws Exception {
        assertEquals(ToolAccess.ADMIN,
                ToolCompatibilityFilter.effectiveAccess(
                        LevelAdminTool.class.getAnnotation(McpTool.class)));
        assertEquals(ToolAccess.READ,
                ToolCompatibilityFilter.effectiveAccess(
                        LevelReadTool.class.getAnnotation(McpTool.class)));
        assertEquals(ToolAccess.WRITE,
                ToolCompatibilityFilter.effectiveAccess(
                        LevelWriteTool.class.getAnnotation(McpTool.class)));
    }
}
