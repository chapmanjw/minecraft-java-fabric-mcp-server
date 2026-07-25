# Version compatibility

The mod ships **one jar per supported Minecraft version**, built from a single source tree by
[Stonecutter](https://stonecutter.kikugie.dev). Each jar is paired with the matching Fabric API
and Fabric Loader versions; using mismatched dependencies is unsupported.

## Supported build matrix (v1.0.1)

| Minecraft | Fabric Loader | Fabric API           | Mappings        | JDK | Loom plugin ID                  |
| --------- | ------------- | -------------------- | --------------- | --- | ------------------------------- |
| 1.21.11   | 0.19.2        | 0.141.5+1.21.11      | Mojang official | 21  | `fabric-loom` (LoomGradlePlugin)         |
| 26.1.1    | 0.19.2        | 0.145.4+26.1.1       | unobfuscated    | 25  | `net.fabricmc.fabric-loom` (LoomNoRemap) |
| 26.1.2    | 0.19.2        | 0.155.2+26.1.2       | unobfuscated    | 25  | `net.fabricmc.fabric-loom` (LoomNoRemap) |
| 26.2      | 0.19.2        | 0.155.2+26.2         | unobfuscated    | 25  | `net.fabricmc.fabric-loom` (LoomNoRemap) |

Each jar declares `depends.minecraft` as the **exact** version it was built against — the 26.2 jar
is `"26.2"`, not `">=26.2"` — so Fabric Loader accepts it on that version and refuses it anywhere
else. Download the jar whose `+suffix` matches your game; installing the wrong one fails at load
with a clear message rather than at runtime on a missing symbol. The targets are deliberately not
interchangeable: 26.2 removed API that 26.1.x has, and 1.21.11 predates the 26 API surface entirely.

The three architectural seams are:

1. **Loom plugin variant.** Fabric Loom 1.16+ ships two plugin IDs from the same JAR. The
   legacy `fabric-loom` ID applies `LoomGradlePlugin` (full remap pipeline) and is required for
   obfuscated Minecraft (≤ 1.21.x). The new `net.fabricmc.fabric-loom` ID applies
   `LoomNoRemapGradlePlugin`, which skips remapping entirely and is required for unobfuscated
   Minecraft (26.1+). Because the two plugin IDs register different DSL surfaces (one exposes
   `mappings` and `modImplementation`, the other does not), the plugin choice has to live in
   the project's `plugins {}` block — which means each Stonecutter version subproject has its
   own `versions/<ver>/build.gradle.kts` file. Shared build logic lives in `mod-build.gradle.kts`
   and is applied via `apply(from = ...)` from each per-version script.
2. **Mappings.** 1.21.11 still ships obfuscated; we map via Mojang's official mappings through
   `loom.officialMojangMappings()`. 26.1.x is unobfuscated — there is no mappings file to apply,
   and the LoomNoRemap plugin doesn't expose the `mappings` configuration at all. Using Mojmap
   names throughout (rather than Yarn) means the same source code compiles against both targets
   without per-version class-name divergence.
3. **JDK.** 26.1.x bytecode targets Java 25; Loom's Minecraft setup enforces this at configure
   time, so Gradle's daemon must run on JDK 25 to build 26.1.x. 1.21.11 still targets Java 21
   via the project's toolchain spec. `gradle.properties` configures
   `org.gradle.java.installations.fromEnv` so Gradle locates both JDKs via standard CI env vars
   (`JDK_21`, `JDK_25`, `JAVA_HOME_21_X64`, `JAVA_HOME_25_X64`).
4. **API surface.** 26.1 introduced source-incompatible changes — `Difficulty.getKey()` →
   `getSerializedName()`, `Entity.getTags()` → `entityTags()`, `ServerLevel.setDayTime()`
   replaced by `ServerClockManager`, `ServerLevelData.getGameRules()` removed in favor of
   `MinecraftServer.getGameRules()`, `BlockState.getValues()` now returns
   `Stream<Property.Value<?>>` instead of `Map<Property, Comparable>`, and several others.
   `MinecraftAdapterImpl.java` handles these with Stonecutter `//? if mc_gte_26 { … } //?} else
   { /*…*/ //?}` blocks. Tools that depend on a 26.1+ feature can declare
   `minMinecraftVersion = "26.1.0"` in their `@McpTool`; tools that depend on a removed API
   declare `maxMinecraftVersion = "1.21.99"`. In practice the tool surface is identical across
   all four targets.

## How the runtime filter works

At server start, `McEnvironment.capture()` reads:

- `SharedConstants.getCurrentVersion().getName()` → the Minecraft version,
- `FabricLoader.getInstance().getModContainer(id)` for each loaded mod → version map.

`ToolCompatibilityFilter` evaluates every `@McpTool`-annotated tool class against this snapshot:

1. **Minecraft version range**: `minMinecraftVersion` ≤ running ≤ `maxMinecraftVersion`.
2. **Fabric Loader version**: `requiredFabricLoaderVersion` predicate (rarely used; empty = any).
3. **Per-required-module version**: each entry in `requiredFabricModules` must be loaded AND its
   loaded version must satisfy the parallel `requiredModuleVersions` entry.

Tools that pass register into the `ToolRegistry` and appear in `tools/list`. Tools that fail are
logged at INFO with a single-line reason and never registered — they're simply absent from the
tool list returned to MCP clients.

Example INFO log entries on a Minecraft 1.21.11 build that lacks `fabric-data-attachment-api-v1`:

```
[minecraft_fabric_mcp/compat] Skipping tool 'data_attachment_get': required module 'fabric-data-attachment-api-v1' is not installed
[minecraft_fabric_mcp/compat] Skipping tool 'data_attachment_set': required module 'fabric-data-attachment-api-v1' is not installed
[minecraft_fabric_mcp/tools] Registered 92 MCP tools (8 skipped due to version/module constraints)
```

## How Stonecutter handles source differences

Stonecutter preprocesses `.java` (and `.gradle.kts`) files with `//?` directives:

```java
//? if mc >= "26.1.0" {
import net.minecraft.world.item.ItemStackTemplate;
ItemStackTemplate stack = ItemStackTemplate.of(ITEM);
//?} else {
/*import net.minecraft.item.ItemStack;
ItemStack stack = new ItemStack(ITEM);*/
//?}
```

At build time, the active subproject's version determines which branch is uncommented. The
checked-in source has the latest-targeted branch active (matching `vcsVersion = "26.1.2"` in
`settings.gradle.kts`), so most developers can read the codebase as if it were always 26.1.

## Runtime data differences (what compiling cannot catch)

A Minecraft version can rename or remove **data** — tag ids, game-rule ids, registry keys, block
states — with no source change at all. Every target compiles, every test passes, and the tools
simply return different values. Adding a target therefore needs a data pass as well as a build.

Known differences on the current matrix:

| Data | 1.21.11 / 26.1.x | 26.2 |
| --- | --- | --- |
| block tag `minecraft:concrete_powder` | present | **renamed** to `minecraft:concrete_powders` |
| block tag `minecraft:concrete` | absent | **added** |
| game-rule ids | snake_case since 26.1 (`spawn_mobs`, `random_tick_speed`) — the old camelCase ids were removed outright | unchanged |

The mod hardcodes no tag or game-rule ids: `tag_*` and `level_*_game_rule` pass through whatever the
running game defines. So these are not mod bugs, and there is nothing to gate. They matter to
*callers*, which must not assume an id seen on one version exists on another.

When adding a Minecraft target, check at minimum: block/item tag ids the build workflows reference,
game-rule ids, registry key names in `Registries` / `BuiltInRegistries`, and command syntax used by
anything routed through `command_execute`. Diff the extracted server jar's `data/minecraft/tags/`
tree between the old and new target — that is how the concrete-powder rename above was found.

## Tool surface differences across versions

The complete cross-version matrix lives in [docs/tools.md](tools.md). High-level summary:

| Feature | 1.21.11 | 26.1.1 | 26.1.2 | 26.2 |
| --- | --- | --- | --- | --- |
| All core tools (server, level, block, entity, player, …) | ✅ | ✅ | ✅ | ✅ |
| `level_get_biome_at`, `level_list_biomes_in_dimension` | ✅ (requires `fabric-biome-api-v1`) | ✅ | ✅ | ✅ |
| `data_attachment_*` | ✅ (requires `fabric-data-attachment-api-v1`) | ✅ | ✅ | ✅ |
| `loot_table_*` | ✅ (requires `fabric-loot-api-v3`) | ✅ | ✅ | ✅ |
| `recipe_*` | ✅ (requires `fabric-recipe-api-v1`) | ✅ | ✅ | ✅ |
| Trading-related tools (planned) | ✅ (via `TradeOfferHelper`) | 🚧 (data-driven trades only) | 🚧 | 🚧 |

A tool whose registration is conditional on a Fabric API module simply doesn't appear in `tools/list`
when that module is missing — there's no error path the client has to handle.

## Client inspection tools (`client` category)

The `client` category (`view_capture`, `sense_*`, `client_status`) is served only by the client
entrypoint (`McpClientMod`) running inside a real client — see
[architecture.md](architecture.md#client-entrypoint-mcpclientmod-and-the-clientaccess-seam) and
[configuration.md](configuration.md#two-mcp-servers-world--inspection). It carries no per-version
`@McpTool` constraints — every client tool is present on every client target, including 26.2.

The load-bearing capture facts hold on all four targets: the only capture entry point is the
callback form `Screenshot.takeScreenshot(RenderTarget, [int downScale,] Consumer<NativeImage>)`
(there is no `NativeImage`-returning overload), and `NativeImage` has no in-memory byte export —
only `writeToFile(File/Path)` — so `view_capture` round-trips a temp PNG.

**26.2 diverged.** Through 26.1.x the client symbols were identical across targets and needed no
Stonecutter split. 26.2 changed three of them, all verified by `javap` against the client jars:

| Symbol | 1.21.11 / 26.1.x | 26.2 |
| --- | --- | --- |
| current screen | `Minecraft.screen` (public field) | `Minecraft.gui.screen()` — moved to `Gui` |
| close a screen | `Minecraft.setScreen(Screen)` | `Minecraft.setScreenAndShow(Screen)` |
| main framebuffer | `Minecraft.getMainRenderTarget()` | `Minecraft.gameRenderer.mainRenderTarget()` |

The screen accessor was *moved*, not deleted: `Minecraft.gui` is `public final Gui` and `Gui.screen()`
is a public getter. No mixin is needed and none is used — this mod ships `"mixins": []`.

**The null check before closing a screen is load-bearing and must not be removed.** `Gui.setScreen(null)`
is not a no-op when no screen is open: its bytecode throws `IllegalStateException` during client
teardown, constructs a `TitleScreen` when the level is gone, constructs a `DeathScreen`, and — for a
dying player on a world with the death screen suppressed — calls `LocalPlayer.respawn()`, which sends
a respawn packet to the server. `view_capture` is declared `readOnly`, so it must never reach that
path. The 26.1.x branch is guarded by `mc.screen != null` and the 26.2 branch by
`mc.gui.screen() != null`, for the same reason.

Note also that `setScreenAndShow` renders an extra out-of-band frame that plain `setScreen` did not.

All client coupling stays localized in `adapter.client.ClientAccessImpl` behind the stable
`ClientAccess` interface, gated with the `mc_gte_26_2` Stonecutter constant. Targets at or below
26.1.x keep the original field reads unchanged.

## Building a different target locally

```sh
./gradlew "Reset active project" -Pversion=1.21.11
./gradlew build
```

The output jar lands at `versions/1.21.11/build/libs/`.

## When a new Minecraft version drops

The intended workflow:

1. Add the version to `settings.gradle.kts` and create `versions/X.Y.Z/build.gradle.kts` +
   `gradle.properties` with the matching Fabric API + Loader coordinates. Copy the nearest existing
   per-version build script — the Loom plugin id depends on whether the target is obfuscated.
2. Add the directory to the `directories` list in `.github/dependabot.yml`. A version directory
   Dependabot is not pointed at goes unwatched, and its plugin pins drift silently.
3. Add the version to the matrices in `.github/workflows/build.yml` AND
   `.github/workflows/release.yml`.
4. Run `./gradlew chiseledBuild` and fix any Stonecutter-block divergences. Add a new constant only
   when an existing one cannot express the split; compare major/minor numerically rather than by
   string (see `mc_gte_26_2`).
5. **Do the runtime-data pass.** The build passing proves only source compatibility. Diff the
   extracted server jar's `data/minecraft/tags/` tree against the previous target, and check
   game-rule ids and registry key names. See "Runtime data differences" above — a tag rename or a
   game-rule rename changes what tools return with nothing to compile against.
6. **Check the client surface separately.** `src/client/` is a different source set; a target can
   build while the client module does not. Verify `view_capture` and every `sense_*` tool.
7. Update this document, the README version tables, `docs/fabric-api-modules.md`, and the setup
   guides.
