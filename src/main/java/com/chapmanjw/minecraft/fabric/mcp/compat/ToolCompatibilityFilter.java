package com.chapmanjw.minecraft.fabric.mcp.compat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chapmanjw.minecraft.fabric.mcp.config.Config;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/**
 * Decides which {@code @McpTool}-annotated classes should be registered against the
 * current {@link McEnvironment} and against the operator's {@link Config}-driven
 * category / access filters. Each rejected tool is logged with a single-line reason so
 * users debugging "why isn't tool X available?" can search the boot log.
 *
 * <p>Config model precedence (see {@link Config}):
 *
 * <ol>
 *   <li>If {@code includedCategories} is non-empty, that is the category allowlist.
 *       Otherwise the {@link ToolCategory#enabledByDefault() default-on} categories
 *       apply.
 *   <li>Subtract {@code excludedCategories}.
 *   <li>Drop any tool whose effective {@link ToolAccess} rank exceeds the configured
 *       {@code maxAccess} cap ({@code excludeWriteTools=true} lowers the cap to
 *       {@code read}).
 * </ol>
 */
public final class ToolCompatibilityFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft_fabric_mcp/compat");

    private static final String VALID_CATEGORIES =
            "blocks, structures, world, entities, players, items, gameplay, scripting, registries, server";

    private final McEnvironment env;

    /**
     * The effective category allowlist: either the user's non-empty
     * {@code includedCategories} or the default-on set, with {@code excludedCategories}
     * already subtracted.
     */
    private final Set<ToolCategory> allowedCategories;

    private final ToolAccess maxAccess;

    public ToolCompatibilityFilter(McEnvironment env) {
        this(env, Config.defaults());
    }

    public ToolCompatibilityFilter(McEnvironment env, Config config) {
        this.env = env;
        Set<ToolCategory> included = parseCategorySet(config.includedCategories(), "included_categories");
        Set<ToolCategory> excluded = parseCategorySet(config.excludedCategories(), "excluded_categories");

        EnumSet<ToolCategory> base;
        if (!included.isEmpty()) {
            base = EnumSet.copyOf(included);
        } else {
            base = EnumSet.noneOf(ToolCategory.class);
            for (ToolCategory c : ToolCategory.values()) {
                if (c.enabledByDefault()) {
                    base.add(c);
                }
            }
        }
        base.removeAll(excluded);
        this.allowedCategories = base;
        this.maxAccess = config.effectiveMaxAccess();
    }

    private static Set<ToolCategory> parseCategorySet(List<String> wireNames, String field) {
        if (wireNames == null || wireNames.isEmpty()) {
            return EnumSet.noneOf(ToolCategory.class);
        }
        EnumSet<ToolCategory> out = EnumSet.noneOf(ToolCategory.class);
        for (String name : wireNames) {
            Optional<ToolCategory> parsed = ToolCategory.fromWireName(name);
            if (parsed.isEmpty()) {
                LOGGER.warn(
                        "Ignoring unknown category '{}' in config.{} (valid: {})",
                        name,
                        field,
                        VALID_CATEGORIES);
                continue;
            }
            out.add(parsed.get());
        }
        return out;
    }

    /**
     * Compute the effective {@link ToolAccess} for a tool from its annotation: {@code admin}
     * wins, then the read-only override / heuristic, otherwise {@code WRITE}.
     */
    static ToolAccess effectiveAccess(McpTool meta) {
        if (meta.admin()) {
            return ToolAccess.ADMIN;
        }
        if (meta.readOnly() || ReadOnlyHeuristic.isReadOnly(meta.name())) {
            return ToolAccess.READ;
        }
        return ToolAccess.WRITE;
    }

    /**
     * Read the {@link McpTool} annotation from {@code toolClass}, evaluate every
     * constraint, and return an Optional containing the descriptor if compatible.
     */
    public Optional<ToolDescriptor> evaluate(Class<?> toolClass) {
        McpTool meta = toolClass.getAnnotation(McpTool.class);
        if (meta == null) {
            throw new IllegalArgumentException(
                    toolClass.getName() + " is not annotated with @McpTool");
        }

        if (!VersionRange.atLeast(env.minecraftVersion(), meta.minMinecraftVersion())) {
            LOGGER.debug(
                    "Skipping tool '{}': Minecraft {} < required min {}",
                    meta.name(),
                    env.minecraftVersion(),
                    meta.minMinecraftVersion());
            return Optional.empty();
        }
        if (!VersionRange.atMost(env.minecraftVersion(), meta.maxMinecraftVersion())) {
            LOGGER.debug(
                    "Skipping tool '{}': Minecraft {} > required max {}",
                    meta.name(),
                    env.minecraftVersion(),
                    meta.maxMinecraftVersion());
            return Optional.empty();
        }

        if (!meta.requiredFabricLoaderVersion().isEmpty()
                && !VersionRange.matches(
                        env.fabricLoaderVersion(), meta.requiredFabricLoaderVersion())) {
            LOGGER.debug(
                    "Skipping tool '{}': Fabric Loader {} does not satisfy '{}'",
                    meta.name(),
                    env.fabricLoaderVersion(),
                    meta.requiredFabricLoaderVersion());
            return Optional.empty();
        }

        String[] modules = meta.requiredFabricModules();
        String[] predicates = meta.requiredModuleVersions();
        List<ToolDescriptor.RequiredModule> required = new ArrayList<>(modules.length);
        for (int i = 0; i < modules.length; i++) {
            String moduleId = modules[i];
            String predicate = i < predicates.length ? predicates[i] : "*";
            required.add(new ToolDescriptor.RequiredModule(moduleId, predicate));

            Optional<String> present = env.moduleVersion(moduleId);
            if (present.isEmpty()) {
                LOGGER.debug(
                        "Skipping tool '{}': required module '{}' is not installed",
                        meta.name(),
                        moduleId);
                return Optional.empty();
            }
            if (!VersionRange.matches(present.get(), predicate)) {
                LOGGER.debug(
                        "Skipping tool '{}': required module '{}' is at {}, need '{}'",
                        meta.name(),
                        moduleId,
                        present.get(),
                        predicate);
                return Optional.empty();
            }
        }

        ToolCategory category = ToolCategory.forToolName(meta.name());
        ToolAccess access = effectiveAccess(meta);

        if (!allowedCategories.contains(category)) {
            LOGGER.debug(
                    "Skipping tool '{}': category '{}' not in active categories {}",
                    meta.name(),
                    category.wireName(),
                    wireNames(allowedCategories));
            return Optional.empty();
        }
        if (access.rank() > maxAccess.rank()) {
            LOGGER.debug(
                    "Skipping tool '{}': access '{}' exceeds max_access '{}'",
                    meta.name(),
                    access.wireName(),
                    maxAccess.wireName());
            return Optional.empty();
        }

        return Optional.of(
                new ToolDescriptor(
                        meta.name(),
                        meta.description(),
                        meta.minMinecraftVersion(),
                        meta.maxMinecraftVersion(),
                        required,
                        meta.requiredFabricLoaderVersion(),
                        category,
                        access,
                        toolClass));
    }

    private static String wireNames(Set<ToolCategory> cats) {
        if (cats.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ToolCategory c : cats) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(c.wireName());
            first = false;
        }
        return sb.append(']').toString();
    }
}
