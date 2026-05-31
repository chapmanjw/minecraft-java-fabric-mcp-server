package com.chapmanjw.minecraft.fabric.mcp.tools.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative metadata for an MCP tool implementation.
 *
 * <p>Place on the tool's implementation class. The compatibility layer reads this at
 * server startup and decides whether to register the tool against the running
 * Minecraft version and installed Fabric API module set.
 *
 * <p>Tools without any version or module constraints (i.e. only {@link #name()} and
 * {@link #description()} populated) register unconditionally on every supported target.
 *
 * <p>Version ranges use Fabric Loader's
 * {@link net.fabricmc.loader.api.metadata.version.VersionPredicate} syntax — for
 * example {@code ">=14.0.0"}, {@code "[1.0.0,2.0.0)"}, or {@code "*"}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface McpTool {

    /** Tool name, e.g. {@code "level_get_biome_at"}. Must be unique across the registry. */
    String name();

    /** Human-readable summary surfaced to MCP clients via {@code tools/list}. */
    String description();

    /**
     * Minimum Minecraft version (inclusive). Empty means "no lower bound".
     * Compared semantically — {@code "1.21.11"} is older than {@code "26.1.1"}.
     */
    String minMinecraftVersion() default "";

    /** Maximum Minecraft version (inclusive). Empty means "no upper bound". */
    String maxMinecraftVersion() default "";

    /**
     * Fabric API module IDs (e.g. {@code "fabric-biome-api-v1"}) that must be loaded
     * for this tool to function. Order matches {@link #requiredModuleVersions()}.
     */
    String[] requiredFabricModules() default {};

    /**
     * Version predicates for each entry in {@link #requiredFabricModules()}, same
     * index. An entry of {@code "*"} or an empty string accepts any version.
     */
    String[] requiredModuleVersions() default {};

    /**
     * Optional Fabric Loader version constraint. Empty means "any loader version".
     * Tools rarely set this — usually module-level constraints are enough.
     */
    String requiredFabricLoaderVersion() default "";

    /**
     * Whether this tool is purely read-only — i.e. does not mutate Minecraft state.
     * Operators can cap the surface to read-only tools via {@code max_access=read} (or
     * the legacy {@code exclude_write_tools=true}) in the config; only tools whose
     * effective access is {@code READ} survive in that mode.
     *
     * <p>Default is {@code false}. The registration filter (
     * {@link com.chapmanjw.minecraft.fabric.mcp.compat.ToolCompatibilityFilter}) also
     * runs {@link com.chapmanjw.minecraft.fabric.mcp.compat.ReadOnlyHeuristic#isReadOnly}
     * on the tool's name; the OR of those two values is the effective read-only flag.
     * In other words: set this to {@code true} only when the heuristic missed the tool;
     * the heuristic catches all standard {@code _get_/_list_/_describe/_check_/_query/etc.}
     * patterns automatically.
     */
    boolean readOnly() default false;

    /**
     * Whether this tool is an admin operation — world-wide, server-lifecycle,
     * destructive, or command-tree. Admin tools are opt-in: they register only when the
     * operator raises {@code max_access} to {@code admin}.
     *
     * <p>The effective access level is computed as:
     * {@code admin() ? ADMIN : (readOnly() || ReadOnlyHeuristic.isReadOnly(name) ? READ : WRITE)}.
     * In other words, {@code admin()} wins over the read-only heuristic — a tool named
     * with a read verb but tagged {@code admin=true} is still {@code ADMIN}.
     *
     * <p>Default is {@code false}.
     */
    boolean admin() default false;
}
