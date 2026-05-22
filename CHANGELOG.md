# Changelog

All notable changes to this project will be documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-05-21

### Removed

- **`AsyncJobRegistry` (and the `jobRegistry` field on `ToolContext`).** Plumbed
  for chunked tool operations but never invoked from production — the 65,536-cell
  cap on `block_*_region` tools keeps every operation within the synchronous
  command-timeout budget. Removed ~150 lines of unused infrastructure plus
  `AsyncJobRegistryTest`. If we need async jobs later, we'll add them back with
  an actual call site driving the design.
- **Streaming-route handler infrastructure** (`StreamingResponse`,
  `StreamingHttpRouteHandler`, `HttpTransport.registerStreamingRoute`, the
  internal `StreamingResponseImpl`). ~250 lines, only ever exercised by tests.
  A real SSE channel for `events_*` belongs in v0.2.0 with a proper design;
  half-built scaffolding rots.

### Fixed

- **`content_registry_set_fuel`** previously threw `AdapterException` on every
  call across every target — the implementation contradicted its own description
  ("no-op"). Now returns `false` to match the documented contract, so clients
  can distinguish "not supported" from server error without a stack trace.
- **`events_subscribe` Fabric-module declaration** extended from one module to
  four (`fabric-lifecycle-events-v1`, `fabric-message-api-v1`,
  `fabric-networking-api-v1`, `fabric-events-interaction-v0`). All four ship in
  the fabric-api umbrella so this is metadata hygiene — but it lets the
  compatibility filter give accurate diagnostics if a future minimal-Fabric
  install lacks one of them.
- **`server_get_status` reported `registeredToolCount: -1`.** The adapter has no
  view of the tool registry, so it returned a sentinel. The `server_get_status`
  tool now fills the real count from the registry, which is threaded through
  `ToolContext`.

### Added

- **Periodic rate-limiter prune** wired into `McpServerMod.onEndTick`. Runs
  every 600 server ticks (≈ 30 s) and drops `RateLimiter` buckets that have
  been at full capacity for 10 minutes or longer. Prevents per-client bucket
  growth on long-running servers with many MCP clients. `onEndTick` is no
  longer a documented no-op.
- **Five builder-facing block tools** for AI-driven construction:
  - **`block_fill_batch`** — apply many fills in one call (up to 8192); the
    efficient way to place a generated/voxelized build instead of hundreds of
    separate requests. Each entry is auto-tiled.
  - **`block_scan_summary`** — server-side material histogram + non-air bounding
    box over a box up to 1,048,576, so reconnaissance never floods the client
    with per-block rows.
  - **`block_get_map_color`** — a block's base map colour (packed `rgb`,
    `#RRGGBB` hex, r/g/b, palette id).
  - **`block_render_region`** — render a region to a PNG (`iso`/`side`/`front`/
    `top`) from block map colours, **server-side with no client/renderer
    dependency**; returns an MCP `image` content block. The verify-time "eyes"
    for representational builds.
- **`ToolResult.addImage` / `ofImage`** — tool results can now carry MCP
  `image` content blocks (used by `block_render_region`).

### Changed

- **`block_fill_region` auto-tiles past the 32,768 `/fill` cap.** Vanilla
  `/fill` silently no-ops above 32,768 blocks; the adapter now splits any fill
  into ≤32,768 sub-boxes server-side (and decomposes oversized hollow/outline
  into faces), so large fills place fully and report the true block count.

### Build

- **JVM toolchains auto-provision via the `foojay-resolver-convention` plugin**
  (`settings.gradle.kts`). `./gradlew build` now fetches a matching JDK (e.g.
  Java 21 for the 1.21.x node) when none is detected locally, so the
  multi-version build works on any machine / CI without manual toolchain paths.
- **Release workflow publishes to Modrinth and CurseForge** (one version per
  Minecraft target) alongside GitHub Releases, via `Kir-Antipov/mc-publish`.

### Refactored

- **`MinecraftAdapterImpl` split into a thin facade + 8 domain helper classes.**
  The 3,213-line monolith is now ~1,160 lines of delegating one-liners over
  `AdapterContext` (shared state + utilities, 222 lines), `BlockOps` (286),
  `EntityOps` (305), `PlayerOps` (391), `WorldOps` (629), `GameplayOps` (428),
  `RegistryOps` (666), and `DataOps` (251). The `MinecraftAdapter` interface
  is unchanged — every caller and test continues to work without modification.
  Constants extracted along the way: `BlockOps.MAX_SCAN_VOLUME` (65 536),
  `EntityOps.ticksPerSecond` (20), `WorldOps.MS_PER_TICK` (50). The
  `CapturingCommandSource` inner class lives on `AdapterContext` and is shared
  by every helper that needs command output capture.

### Fixed

- **`entitySummon` returned the wrong UUID.** The post-summon scan picked the entity
  with the *highest* `tickCount` (oldest), inverting the intent. Now snapshots entity
  UUIDs in the spawn box before and after the `/summon` command and returns the UUID
  that's actually new — falling back to lowest-tickCount only when multiple entities
  appear simultaneously. Without this fix, every chained `entityTeleport` /
  `entitySetNbt` after a summon would have targeted a random nearby mob.
- **`entityApplyEffect` silently extended short effects.** Duration is documented in
  ticks but the implementation did `Math.max(1, durationTicks / 20)` (floor division),
  turning a 5-tick request into 20 ticks. Now uses ceiling division so any positive
  duration rounds up to at least 1 second, the minimum vanilla `/effect give` accepts.
- **`ConfigLoader` leaked the config-file `BufferedReader` on parse errors.** On
  Windows this prevented the subsequent token-persistence write to the same file.
  Now closes via try-with-resources.
- **`ConfigLoader.persistTokenToConfigFile` NPE'd when the config path had no parent
  component** (e.g. a bare `config.json` relative to the working directory). Guards
  `Path.getParent()` and rejects paths that resolve to a directory.
- **JSON-RPC empty-batch handling.** Per JSON-RPC 2.0 §6, an empty batch is itself an
  Invalid Request — the server MUST respond with a single error object. Previous
  behavior returned 204 No Content.
- **Port-bind failures now log a one-line actionable hint** ("port already in use —
  pick a free port via `port` in config.json or `MCP_PORT`") instead of a raw
  `BindException` stack trace. Full stack still available at DEBUG.

### Changed

- **`scoreboardTeamRemoveMember` documents its vanilla limitation.** Minecraft's
  `/team leave <player>` is unscoped — it removes the player from whichever team
  they're on, regardless of the `teamName` argument passed by the caller. Adapter
  interface javadoc now flags this and recommends a membership check before calling.
- **`ToolCompatibilityFilter` per-tool skip logs demoted from INFO to DEBUG.** With
  restrictive category configs the boot log was flooded with one INFO line per
  skipped tool. The aggregate "Registered N tool(s)" INFO summary is preserved;
  warnings for unknown categories remain at WARN.
- **Tool descriptions tightened.** `block_set_state`, `bossbar_set_players`, and
  `entity_get_components` rewritten — the first two were too terse to disambiguate
  similar tools, and the third didn't warn callers about its known limitation
  (returns empty map for non-player entities).
- **`docs/tools.md` tool count corrected** from "~110" to 173.

- **Tool results now emit TOON (Token-Oriented Object Notation) instead of JSON.**
  Structured payloads are serialized via the new
  `com.chapmanjw.minecraft.fabric.mcp.protocol.Toon` encoder (TOON spec v3.2,
  see https://toonformat.dev) and placed in the standard MCP text content block.
  Wins:
  - **No more dual emission.** The redundant `text + structuredContent` pattern
    is gone. Each tool returns one text block; the TOON-encoded payload IS the
    machine-parseable answer. Clients no longer pay for the same data twice.
  - **30–60% token reduction** on structured responses vs equivalent JSON.
    Uniform object arrays (inventory slots, scoreboard rows, recipe ingredient
    lists, etc.) collapse to TOON tabular form (`[N]{f1,f2}:` header + one row
    per line) which is dramatically denser than the JSON-of-objects shape.
  - **Prose summary strings dropped** at every tool call site where they merely
    restated values already present in the structured payload (~60 call sites
    across 29 tool classes). A handful of sites preserved data that lived only
    in the summary string by adding it as a field on the payload (e.g.
    `BlockTools.GetState` now emits `position` in the TOON output instead of
    in a parallel summary string).
- **`ToolResult` API.** Removed `ofTextAndStructured`, `withStructured`, and
  the `structuredContent()` accessor. Added `ToolResult.ofToon(JsonNode)` and
  `BaseTool.okToon(JsonNode)`. `McpDispatcher` no longer emits the
  `structuredContent` field in the `tools/call` response envelope.
- **`EntityInfo` slimmed.** Removed the session-ephemeral `networkId` field;
  the stable `uuid` is sufficient for any cross-tool reference. The adapter
  method `entityGetByNetworkId` is preserved for internal vanilla-networking
  callers but is no longer surfaced via the DTO.

### Added

- **Tool surface trimming via category filters.** Three new config fields let
  operators shrink the registered tool set without source changes:
  - `included_categories` (env `MCP_INCLUDED_CATEGORIES`, CSV) — if non-empty,
    only tools whose category is in the list are registered.
  - `excluded_categories` (env `MCP_EXCLUDED_CATEGORIES`, CSV) — tools in any
    listed category are dropped. Applied after `included_categories`.
  - `exclude_write_tools` (env `MCP_EXCLUDE_WRITE_TOOLS`, boolean) — when true,
    only read-only inspection tools are exposed. Useful for observer agents.

  Tools are grouped into five wire-named categories: `world` (blocks, structures,
  level, worldborder), `actors` (entities, players, inventory, item stacks),
  `gameplay` (scoreboards, bossbars, advancements, commands, schedules,
  functions, events), `registries` (recipes, loot, tags, content registries,
  fluid + data storage), and `server` (lifecycle, datapacks).

  Read-only classification combines a name-pattern heuristic (matches `_get_`,
  `_list_`, `_describe`, `_query`, `_evaluate`, `_count_`, `_check_`, etc.) with
  an optional `@McpTool(readOnly = true)` annotation override. Skipped tools
  log a single line at boot so debugging "where did tool X go?" is greppable.
- **44 new MCP tools** across ten domains, rounding out Fabric API parity and
  the vanilla command surface:
  - Content registry (`content_registry_*`, 6 tools) — fuel burn time, flammable
    block parameters, and composter level-up chance. Uses
    `fabric-content-registries-v0`. Read paths cover both the 1.21.11 (`CompostingChanceRegistry`,
    `FuelRegistryEvents`) and 26.1.x (`CompostableRegistry`, `FuelValueEvents`) class
    moves via Stonecutter blocks. `FlammableBlockRegistry.Entry` accessors also
    renamed between versions (`getBurnChance/getSpreadChance` → `getBurnOdds/getIgniteOdds`).
  - Resource condition (`resource_condition_evaluate`, 1 tool) — decode and
    evaluate `ResourceCondition` JSON against the live registry. Uses
    `fabric-resource-conditions-api-v1`.
  - Fluid storage (`fluid_storage_*`, 2 tools) — read fluid tanks from any
    block exposing `FluidStorage.SIDED`. Uses `fabric-transfer-api-v1`.
  - Player screen (`player_screen_*`, 3 tools) — open standard menus,
    open a block container, close the open screen for a player. Uses
    `fabric-screen-handler-api-v1`.
  - Bossbar (`bossbar_*`, 11 tools) — full coverage of vanilla `/bossbar`.
  - Advancement (`advancement_*`, 5 tools) — grant/revoke/list/get-definition
    for player advancements.
  - Function (`function_*`, 3 tools) — run, list, and read definitions of
    datapack functions.
  - World border (`worldborder_*`, 8 tools) — full coverage of `/worldborder`.
  - Schedule (`schedule_*`, 3 tools) — schedule, clear, and list scheduled
    function entries.
  - Item modify (`item_modify_*`, 2 tools) — entity-slot and block-slot
    variants of the vanilla `/item modify` command.
- New DTOs in `adapter/dto/`: `BossbarInfo`, `WorldBorderInfo`,
  `AdvancementProgressInfo`, `ScheduledFunctionInfo`, `FluidStackInfo`,
  `FlammableBlockInfo`, `CompostableInfo`.
- `MinecraftAdapter` interface extended with the corresponding read and
  command-dispatch methods; `MinecraftAdapterImpl` ships the implementations
  with Stonecutter blocks isolating per-version API divergence.

### Known limitations

- `content_registry_set_fuel` is stubbed — Fabric's `FuelValueEvents` /
  `FuelRegistryEvents` only fire during resource reload. The tool returns an
  actionable error rather than silently no-oping. Runtime fuel mutation
  remains unavailable until Fabric exposes an in-place setter.
- `function_get_definition` returns the `CommandFunction.toString()` form
  (function id plus entry count). The internal entry-list accessors are not
  stable across mapping versions; a future iteration can produce a richer
  view via per-version Stonecutter blocks if downstream callers need it.
- `schedule_list` parses the feedback lines from the `/schedule` command
  because vanilla's `TimerQueue` does not publish a stable iteration API
  across versions.

### Known limitations carried into v0.1.0

- `level_get_dimension_info` (adapter method `levelGetDimensionInfo`) is stubbed for the
  Minecraft 1.21.11 target. The 1.21.11 `DimensionType` is a record without the
  `ultraWarm()`, `piglinSafe()`, and `natural()` accessors used to populate
  `DimensionInfo`. A v0.2.0 fix will derive those flags from the dimension Identifier
  plus the `DimensionType.MonsterSettings` / coordinate-scale data.
- On the Minecraft 26.1.x targets, `level_set_time` and `level_set_weather` dispatch
  through Brigadier (`/time set …`, `/weather …`) rather than the direct `ServerLevel`
  setters used on 1.21.11. The 26.1.x mojmap surface dropped `ServerLevel.setDayTime`
  and `ServerLevel.setWeatherParameters` (clock and weather now live on
  `ServerClockManager` and `WeatherData`); using the command path keeps the adapter
  API identical across versions without reaching into per-version internals. v0.2.0
  will switch to the typed API where it remains stable.

## [0.1.0] - 2026-05-19

### Added

- **Multi-version Fabric mod** built with [Stonecutter](https://stonecutter.kikugie.dev):
  Minecraft 1.21.11 (Mojang mappings, JDK 21), 26.1.1 and 26.1.2 (unobfuscated JAR, JDK 25).
  Each Stonecutter version subproject has its own `versions/<ver>/build.gradle.kts` because
  Fabric Loom ships two distinct plugin IDs (`fabric-loom` vs `net.fabricmc.fabric-loom` for
  the LoomNoRemap variant), and the plugin ID has to be in the subproject's `plugins{}` block.
- **Embedded JDK HTTP transport** with strict Host / Origin / body-size / rate-limit checks and
  optional bearer-token auth. Defaults to localhost-only, no token, no friction for single-player.
- **MCP JSON-RPC dispatch** implementing the 2025-06-18 protocol revision over Streamable HTTP.
- **Main-thread executor + bounded event ring buffer + async job registry**, ensuring every
  Minecraft API touch happens on the server main thread without long blocking calls.
- **Version-aware tool registration**: tools declare Minecraft version and Fabric API module
  constraints via `@McpTool`; the registration filter drops incompatible tools at startup with a
  single-line INFO log.
- **~100 MCP tools** across 14 domains (server, level, block, blockEntity, entity, player,
  inventory, itemstack, command, scoreboard, data, structure, datapack, loot/recipe/tag/resource,
  events). Tool naming follows the Minecraft Java API class convention
  (`level_set_block`, not `mc_block_set`).
- **Event subscription system** (`events_subscribe` / `events_poll` / `events_unsubscribe`)
  with filterable per-subscription ring buffers and 20 supported event types covering
  server / player / entity / block / item / container.
- **`MinecraftAdapter` interface and production implementation** that uses direct API for reads
  and Brigadier command dispatch for writes, keeping the cross-version surface tractable.
- **Full documentation set**: README, architecture, tools reference, fabric-api-modules,
  configuration, security, single-player and dedicated-server setup, Claude Desktop and Cursor
  integration, version compatibility, troubleshooting.
- **CI/CD**: matrix build across all three Minecraft targets (per-target JDK install via
  `actions/setup-java`), a `chiseledBuild` aggregate job that exercises the full matrix in a
  single Gradle invocation, Spotless + Checkstyle lint, GitHub Releases publication.
  Modrinth and CurseForge publishers are scaffolded as commented blocks
  pending org secrets.
- **Unit tests** for config loading + env-var override precedence, constant-time token compare,
  Host / Origin / bearer security filter, rate limiter, main-thread executor, event ring buffer,
  async job registry, MCP dispatcher (initialize / tools list / tools call / errors).

### Known limitations in 0.1.0

All ~110 registered tools have working adapter implementations against the live Minecraft
server. The nuances:

- `entity_get_components` returns an empty map — vanilla `Entity` doesn't expose a typed
  component view the way `ItemStack` does. v0.2.0 will surface the data-attachment map here.
- `command_register` accepts the call but doesn't actually register a custom command — the
  wire schema doesn't yet carry a webhook target, and the dispatcher needs a back-channel
  to surface invocations as `command.*` events. v0.2.0.
- `level_set_time` / `level_set_weather` go through Brigadier (`/time set …`, `/weather …`)
  on the 26.1.x targets because the typed setters were dropped from `ServerLevel` in 26.1
  (clock and weather now live on `ServerClockManager` and `WeatherData`). The semantics are
  identical to the typed-API path used on 1.21.11.

### Stability statement

The 0.1.0 public contract — tool names, tool input/output schemas, configuration schema and
env-var names, and the MCP wire protocol revision — is governed by SemVer from this release
forward.

[Unreleased]: https://github.com/chapmanjw/minecraft-java-fabric-mcp-server/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/chapmanjw/minecraft-java-fabric-mcp-server/releases/tag/v0.1.0
