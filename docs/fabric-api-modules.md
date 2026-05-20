# Fabric API modules

The mod depends on the **server-side surface of the Fabric API** to expose Minecraft features
as MCP tools. This document catalogues every Fabric API module relevant to the project, the
tools that depend on it, and known compatibility differences across our build matrix.

## Required vs optional modules

When you install Fabric API, you get every module. The mod's tool registration filter checks for
specific modules at startup and skips tools whose dependencies aren't loaded — useful when a
stripped-down server install omits modules, but in practice "install Fabric API" gives you
everything below.

## Catalog

### Core utilities

| Module | Purpose | Used by |
| --- | --- | --- |
| `fabric-api-base` | Shared base utilities | Always loaded as a transitive of every other module. |
| `fabric-api-lookup-api-v1` | Capability/lookup pattern | Internal — used by inventory and data tools for cross-mod compatibility. |
| `fabric-transitive-access-wideners-v1` | Transitive access wideners | Internal — exposes Minecraft internals our adapter implementation needs. |

### Lifecycle and events

| Module | Used by |
| --- | --- |
| `fabric-lifecycle-events-v1` | `events_subscribe`, `events_poll`, the McpServerMod startup hooks. **Always required.** |
| `fabric-entity-events-v1` | `entity.spawn`, `entity.death`, `entity.load`, `entity.unload` event types. |
| `fabric-events-interaction-v0` | `block.break`, `block.place`, `block.use`, `item.use` event types. |
| `fabric-message-api-v1` | `player.chat` event type, `player_send_message`. |
| `fabric-crash-report-info-v1` | Crash report extension — used to surface MCP-specific diagnostic state in crash reports. |

### Commands

| Module | Used by |
| --- | --- |
| `fabric-command-api-v2` | `command_execute`, `command_execute_as`, `command_register`. We use v2 exclusively. |

`fabric-command-api-v1` was removed in 26.1; we never depended on it.

### World, blocks, dimensions

| Module | Used by |
| --- | --- |
| `fabric-block-api-v1` | Block-state inspection (`block_get_state`, etc.). |
| `fabric-block-view-api-v2` | Internal — block view extensions for `block_scan_region`. |
| `fabric-biome-api-v1` | `level_get_biome_at`, `level_list_biomes_in_dimension`. |
| `fabric-dimensions-v1` | `entity_teleport` (cross-dimension), `level_list_dimensions`. |
| `fabric-game-rule-api-v1` | `level_set_game_rule` (set; reads work without it). |

### Entities and items

| Module | Used by |
| --- | --- |
| `fabric-item-api-v1` | `itemstack_describe`, `itemstack_drop_at`, `player_give_item`. |
| `fabric-item-group-api-v1` | Internal — for tools that need creative-tab introspection. |
| `fabric-data-attachment-api-v1` | `data_attachment_*` tools. |

### Inventory and screen handlers

| Module | Used by |
| --- | --- |
| `fabric-screen-handler-api-v1` | `player_screen_open_menu` / `player_screen_open_container` / `player_screen_close`. Also used internally when inspecting container block entities. |
| `fabric-transfer-api-v1` | `fluid_storage_get` / `fluid_storage_list_at`, plus internal use by `inventory_count_items` for fluid-and-item containers. |

### Data, recipes, loot, registries

| Module | Used by |
| --- | --- |
| `fabric-content-registries-v0` | `content_registry_get_fuel` / `content_registry_set_fuel` (read-only on current Fabric) / `content_registry_is_flammable_block` / `content_registry_set_flammable_block` / `content_registry_is_compostable` / `content_registry_set_compostable`. |
| `fabric-loot-api-v3` | `loot_table_*` tools. |
| `fabric-recipe-api-v1` | `recipe_*` tools. |
| `fabric-registry-sync-v0` | Internal — registry access for tag tools. |
| `fabric-resource-conditions-api-v1` | `resource_condition_evaluate`. |
| `fabric-resource-loader-v0` | `resource_loader_*` tools. |
| `fabric-convention-tags-v2` | `tag_*` tools. |
| `fabric-data-generation-api-v1` | Build-time only — used by datagen tasks in our Gradle build, not at runtime. |

### Networking

| Module | Used by |
| --- | --- |
| `fabric-networking-api-v1` | Internal — used for tools that send custom packets (reserved for v0.2.0). |

### Misc

| Module | Used by |
| --- | --- |
| `fabric-particles-v1` | `level_spawn_particle` for custom particles (vanilla particles work without it). |
| `fabric-sound-api-v1` | Internal — used by `level_play_sound` / `player_play_sound` for custom sounds. |
| `fabric-gametest-api-v1` | Test-time only — used by our gametest harness. |

## Removed in 26.1

The following modules / helpers were removed in Minecraft 26.1.x and are NOT in our matrix:

| What | Reason |
| --- | --- |
| `TradeOfferHelper` | Villager trading became data-driven; tools that mutate trades would need a 1.21-only path. We skip them for v0.1.0. |
| `HudRenderCallback` | Replaced by `HudElementRegistry`. Client-only — not relevant to this server-side mod. |
| Deprecated conventional tags previously labelled for 1.22 removal | The deprecated tag aliases were removed; modules now expose only the canonical names. |

## Cross-version availability summary

| Module | 1.21.11 | 26.1.1 | 26.1.2 |
| --- | --- | --- | --- |
| `fabric-lifecycle-events-v1` | ✅ | ✅ | ✅ |
| `fabric-command-api-v1` | ✅ (deprecated) | ❌ | ❌ |
| `fabric-command-api-v2` | ✅ | ✅ | ✅ |
| `fabric-biome-api-v1` | ✅ | ✅ | ✅ |
| `fabric-data-attachment-api-v1` | ✅ | ✅ | ✅ |
| `fabric-loot-api-v3` | ✅ | ✅ | ✅ |
| `fabric-recipe-api-v1` | ✅ | ✅ | ✅ |
| `fabric-particles-v1` | ✅ | ✅ | ✅ |
| `fabric-message-api-v1` | ✅ | ✅ | ✅ |
| `TradeOfferHelper` (utility) | ✅ | ❌ | ❌ |

A tool whose `@McpTool` annotation lists a module that isn't loaded simply doesn't register — see
[docs/version-compatibility.md](version-compatibility.md) for how the filter works at runtime.

## When a Fabric API release adds a new module

If a new release adds a module useful for a new MCP tool:

1. Add the tool class with `@McpTool(... requiredFabricModules = { "fabric-NEW-module" })`.
2. Add the tool class to `ToolRegistration.ALL_TOOL_CLASSES`.
3. Update this catalog.
4. Bump the Fabric API version in the per-version `versions/<ver>/gradle.properties` once the
   module is widely available.
