package com.chapmanjw.minecraft.fabric.mcp.compat;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * High-level grouping of tools, used by the registration filter so an operator can
 * include or exclude entire swathes of the surface via {@link
 * com.chapmanjw.minecraft.fabric.mcp.config.Config#includedCategories()} /
 * {@link com.chapmanjw.minecraft.fabric.mcp.config.Config#excludedCategories()}.
 *
 * <p>Categories map 1:1 from the tool name's domain prefix (the first one or two
 * underscore-separated segments). Adding a new domain means adding one entry to
 * {@link #DOMAIN_TO_CATEGORY}. The mapping is deliberately closed: a domain not in
 * the map raises an exception at startup so we don't silently miscategorize tools.
 *
 * <p>The five categories chosen here align with the natural operator-vs-builder
 * mental model: builders care mostly about {@link #WORLD} + {@link #ACTORS};
 * operators care about {@link #SERVER} + {@link #GAMEPLAY}; data-driven workflows
 * use {@link #REGISTRIES}.
 */
public enum ToolCategory {

    /** Block / structure / level / worldborder — anything geometry- or environment-shaped. */
    WORLD,

    /** Entity / player / inventory / itemstack — anything that moves or holds items. */
    ACTORS,

    /** Game-state logic: scoreboards, bossbars, advancements, schedules, functions, commands, events. */
    GAMEPLAY,

    /** Read or configure registry data: recipes, loot, tags, content registries, fluid storage, data attachments. */
    REGISTRIES,

    /** Server lifecycle / admin: motd, save, reload, datapacks. */
    SERVER;

    /**
     * Static mapping from each known tool-name domain prefix to its category.
     * Domain = the longest prefix that uniquely identifies a tool's bucket — usually
     * the first segment ({@code block_*}, {@code level_*}), occasionally two
     * ({@code block_entity_*}, {@code content_registry_*}, {@code data_storage_*}).
     */
    private static final Map<String, ToolCategory> DOMAIN_TO_CATEGORY = Map.<String, ToolCategory>ofEntries(
            // world
            Map.entry("block", WORLD),
            Map.entry("block_entity", WORLD),
            Map.entry("level", WORLD),
            Map.entry("structure", WORLD),
            Map.entry("worldborder", WORLD),
            // actors
            Map.entry("entity", ACTORS),
            Map.entry("player", ACTORS),
            Map.entry("player_screen", ACTORS),
            Map.entry("inventory", ACTORS),
            Map.entry("itemstack", ACTORS),
            Map.entry("item_modify", ACTORS),
            // gameplay
            Map.entry("scoreboard", GAMEPLAY),
            Map.entry("bossbar", GAMEPLAY),
            Map.entry("advancement", GAMEPLAY),
            Map.entry("command", GAMEPLAY),
            Map.entry("function", GAMEPLAY),
            Map.entry("schedule", GAMEPLAY),
            Map.entry("events", GAMEPLAY),
            // registries
            Map.entry("content_registry", REGISTRIES),
            Map.entry("loot_table", REGISTRIES),
            Map.entry("recipe", REGISTRIES),
            Map.entry("tag", REGISTRIES),
            Map.entry("resource_loader", REGISTRIES),
            Map.entry("resource_condition", REGISTRIES),
            Map.entry("fluid_storage", REGISTRIES),
            Map.entry("data_storage", REGISTRIES),
            Map.entry("data_attachment", REGISTRIES),
            // server
            Map.entry("server", SERVER),
            Map.entry("datapack", SERVER));

    /**
     * Resolve the category for a tool wire-name. Tries the longest two-segment prefix
     * first (so {@code block_entity_*} doesn't land in the bare {@code block} bucket),
     * then falls back to the first segment.
     *
     * @throws IllegalArgumentException if no mapping is registered for the tool's domain.
     */
    public static ToolCategory forToolName(String toolName) {
        if (toolName == null || toolName.isEmpty()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        // Try multi-segment domains first; map is small, exhaustive scan is fine.
        for (var e : DOMAIN_TO_CATEGORY.entrySet()) {
            String domain = e.getKey();
            if (domain.contains("_") && (toolName.equals(domain) || toolName.startsWith(domain + "_"))) {
                return e.getValue();
            }
        }
        int underscore = toolName.indexOf('_');
        String firstSegment = underscore < 0 ? toolName : toolName.substring(0, underscore);
        ToolCategory cat = DOMAIN_TO_CATEGORY.get(firstSegment);
        if (cat == null) {
            throw new IllegalArgumentException(
                    "No category mapping for tool '"
                            + toolName
                            + "' (domain prefix '"
                            + firstSegment
                            + "'). Update ToolCategory.DOMAIN_TO_CATEGORY.");
        }
        return cat;
    }

    /**
     * Lookup a category by its lower-case wire name (e.g. {@code "world"}, {@code "actors"}).
     * Used by Config to parse user-supplied includes/excludes.
     */
    public static Optional<ToolCategory> fromWireName(String wireName) {
        if (wireName == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(ToolCategory.valueOf(wireName.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Stable lower-case identifier safe to use in config files and CLI args. */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
