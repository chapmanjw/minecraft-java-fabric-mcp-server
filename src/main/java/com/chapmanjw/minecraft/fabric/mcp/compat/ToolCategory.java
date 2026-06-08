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
 * <p>The ten categories track the underlying Minecraft subsystems one-for-one so an
 * operator can reason about the surface without learning a separate taxonomy. Seven
 * are on by default ({@link #enabledByDefault()}); the remaining three
 * ({@link #PLAYERS}, {@link #GAMEPLAY}, {@link #REGISTRIES}) are opt-in so a fresh
 * install exposes a lean, builder-focused surface.
 */
public enum ToolCategory {

    /** Blocks and block entities — geometry and the tile-data behind it. */
    BLOCKS,

    /** Saved/loaded structure templates and the structure block file store. */
    STRUCTURES,

    /** Level state and the world border — time, weather, biomes, dimensions, spawn. */
    WORLD,

    /** Entities — summon, query, move, damage, effects, tags. */
    ENTITIES,

    /** Players — info, inventory access, messaging, gamemode, spawn, screens. */
    PLAYERS,

    /** Items — inventory slots, item stacks, item modifiers. */
    ITEMS,

    /** Game-state logic: scoreboards, bossbars, advancements. */
    GAMEPLAY,

    /**
     * Command/function automation: commands, functions, schedules, events, and the
     * data storage / attachment stores agents script against.
     */
    SCRIPTING,

    /**
     * Read or configure registry data: recipes, loot, tags, content registries,
     * resource loading/conditions, fluid storage.
     */
    REGISTRIES,

    /** Server lifecycle / admin: status, motd, save, reload, datapacks. */
    SERVER,

    /**
     * Client-only inspection: capture the rendered first-person frame and read client-side
     * perception (crosshair, raycast, nearby entities, open screen). These tools only exist on
     * the client MCP server ({@code McpClientMod}); a dedicated server never registers them
     * (they are not in {@code ToolRegistration.ALL_TOOL_CLASSES}). Opt-in like the other
     * non-core categories.
     */
    CLIENT;

    /**
     * Static mapping from each known tool-name domain prefix to its category.
     * Domain = the longest prefix that uniquely identifies a tool's bucket — usually
     * the first segment ({@code block_*}, {@code level_*}), occasionally two
     * ({@code block_entity_*}, {@code content_registry_*}, {@code data_storage_*}).
     */
    private static final Map<String, ToolCategory> DOMAIN_TO_CATEGORY = Map.<String, ToolCategory>ofEntries(
            // blocks
            Map.entry("block", BLOCKS),
            Map.entry("block_entity", BLOCKS),
            // structures
            Map.entry("structure", STRUCTURES),
            // world
            Map.entry("level", WORLD),
            Map.entry("worldborder", WORLD),
            // entities
            Map.entry("entity", ENTITIES),
            // players
            Map.entry("player", PLAYERS),
            Map.entry("player_screen", PLAYERS),
            // items
            Map.entry("inventory", ITEMS),
            Map.entry("itemstack", ITEMS),
            Map.entry("item_modify", ITEMS),
            // gameplay
            Map.entry("scoreboard", GAMEPLAY),
            Map.entry("bossbar", GAMEPLAY),
            Map.entry("advancement", GAMEPLAY),
            // scripting
            Map.entry("command", SCRIPTING),
            Map.entry("function", SCRIPTING),
            Map.entry("schedule", SCRIPTING),
            Map.entry("events", SCRIPTING),
            Map.entry("data_storage", SCRIPTING),
            Map.entry("data_attachment", SCRIPTING),
            // registries
            Map.entry("recipe", REGISTRIES),
            Map.entry("loot_table", REGISTRIES),
            Map.entry("tag", REGISTRIES),
            Map.entry("content_registry", REGISTRIES),
            Map.entry("resource_loader", REGISTRIES),
            Map.entry("resource_condition", REGISTRIES),
            Map.entry("fluid_storage", REGISTRIES),
            // server
            Map.entry("server", SERVER),
            Map.entry("datapack", SERVER),
            // client (inspection-only; registered solely by McpClientMod)
            Map.entry("view", CLIENT),
            Map.entry("sense", CLIENT),
            Map.entry("client", CLIENT));

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
     * Lookup a category by its lower-case wire name (e.g. {@code "blocks"}, {@code "world"}).
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

    /**
     * Whether this category is exposed when no {@code included_categories} allowlist is
     * configured. The default-on set is the builder/operator core; {@link #PLAYERS},
     * {@link #GAMEPLAY}, and {@link #REGISTRIES} are opt-in.
     */
    public boolean enabledByDefault() {
        return switch (this) {
            case BLOCKS, STRUCTURES, WORLD, ENTITIES, ITEMS, SCRIPTING, SERVER -> true;
            case PLAYERS, GAMEPLAY, REGISTRIES, CLIENT -> false;
        };
    }
}
