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
 * category / write-tool filters. Each rejected tool is logged with a single-line
 * reason so users debugging "why isn't tool X available?" can search the boot log.
 */
public final class ToolCompatibilityFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft_fabric_mcp/compat");

    private final McEnvironment env;
    private final Set<ToolCategory> includedCategories;
    private final Set<ToolCategory> excludedCategories;
    private final boolean excludeWriteTools;

    public ToolCompatibilityFilter(McEnvironment env) {
        this(env, Config.defaults());
    }

    public ToolCompatibilityFilter(McEnvironment env, Config config) {
        this.env = env;
        this.includedCategories = parseCategorySet(config.includedCategories(), "included_categories");
        this.excludedCategories = parseCategorySet(config.excludedCategories(), "excluded_categories");
        this.excludeWriteTools = config.excludeWriteTools();
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
                        "Ignoring unknown category '{}' in config.{} "
                                + "(valid: world, actors, gameplay, registries, server)",
                        name,
                        field);
                continue;
            }
            out.add(parsed.get());
        }
        return out;
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
        boolean readOnly = meta.readOnly() || ReadOnlyHeuristic.isReadOnly(meta.name());

        if (!includedCategories.isEmpty() && !includedCategories.contains(category)) {
            LOGGER.debug(
                    "Skipping tool '{}': category '{}' not in includedCategories {}",
                    meta.name(),
                    category.wireName(),
                    wireNames(includedCategories));
            return Optional.empty();
        }
        if (excludedCategories.contains(category)) {
            LOGGER.debug(
                    "Skipping tool '{}': category '{}' is excluded",
                    meta.name(),
                    category.wireName());
            return Optional.empty();
        }
        if (excludeWriteTools && !readOnly) {
            LOGGER.debug(
                    "Skipping tool '{}': excludeWriteTools=true and tool is not read-only",
                    meta.name());
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
                        readOnly,
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
