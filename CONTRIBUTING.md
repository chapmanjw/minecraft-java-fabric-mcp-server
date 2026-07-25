# Contributing

Thanks for considering a contribution. This project is small but the surface area is wide —
~100 MCP tools across a multi-version Fabric mod — so a few conventions keep the codebase
maintainable.

## Development setup

Requirements:

- **JDK 21** AND **JDK 25**. Two JDKs are needed because 1.21.x targets Java 21 and 26.1.x
  targets Java 25; Loom's Minecraft-version setup additionally requires the Gradle daemon to
  run on JDK 25 in order to configure the 26.1.x subprojects. Recommended distribution:
  Amazon Corretto for both. Set `JAVA_HOME` to the JDK 25 install; Gradle's toolchain locator
  finds JDK 21 automatically via auto-detection (default Corretto / Temurin install paths) or
  via the `JDK_21` / `JAVA_HOME_21_X64` environment variables.
- A local checkout of this repository.

```sh
git clone https://github.com/chapmanjw/minecraft-java-fabric-mcp-server.git
cd minecraft-java-fabric-mcp-server

# Build every Minecraft target — what CI runs
./gradlew chiseledBuild

# Build a single version
./gradlew :1.21.11:build
./gradlew :26.1.2:build

# Launch a dev Minecraft server with the mod loaded (uses the active subproject)
./gradlew :26.1.2:runServer

# Switch the active Stonecutter version (used as the default for `./gradlew build`)
./gradlew "Reset active project" -Pversion=1.21.11
```

The build downloads everything it needs the first time (~500 MB). Subsequent builds run from the
Gradle cache.

### Project layout

```
.
├── build.gradle.kts                 # minimal root script; applies Stonecutter
├── stonecutter.gradle.kts           # registers the chiseledBuild aggregate task
├── settings.gradle.kts              # pluginManagement + Stonecutter version matrix
├── mod-build.gradle.kts             # shared build logic, applied from each per-version script
└── versions/
    ├── 1.21.11/
    │   ├── build.gradle.kts         # uses id("fabric-loom") — legacy LoomGradlePlugin
    │   └── gradle.properties        # 1.21.11 deps + JDK 21
    ├── 26.1.1/
    │   ├── build.gradle.kts         # uses id("net.fabricmc.fabric-loom") — LoomNoRemap
    │   └── gradle.properties        # 26.1.1 deps + JDK 25
    ├── 26.1.2/
    │   ├── build.gradle.kts         # uses id("net.fabricmc.fabric-loom") — LoomNoRemap
    │   └── gradle.properties        # 26.1.2 deps + JDK 25
    └── 26.2/
        ├── build.gradle.kts         # uses id("net.fabricmc.fabric-loom") — LoomNoRemap
        └── gradle.properties        # 26.2 deps + JDK 25
```

The per-version split is intentional: Fabric Loom 1.16+ ships two plugin IDs (`fabric-loom`
and `net.fabricmc.fabric-loom`) that register different DSL surfaces, and the choice of
plugin ID must happen in a project's own `plugins {}` block. Sharing the central script via
Stonecutter's `centralScript` mechanism doesn't accommodate per-subproject plugin selection,
so each version subproject has its own thin `build.gradle.kts` that picks the plugin and
delegates the rest to `mod-build.gradle.kts`.

## Code style

- **Spotless** with the Google Java Format. Run `./gradlew spotlessApply` before pushing.
- **Checkstyle** enforces the conventions in `config/checkstyle/checkstyle.xml`. 120-char lines,
  Sun-style indents, no star imports.
- **License headers** on every `.java` file (`config/spotless/license-header.txt`). Spotless
  adds them automatically.
- **No emojis in source files** unless the user-visible string really benefits from one.
- **Public methods on the protocol / transport / runtime / compat layers require Javadoc.**
  Tool implementations are described in `docs/tools.md` and may skip per-method Javadoc.

## Adding a new tool

1. Decide which domain the tool belongs to (server, level, block, entity, etc.). The directory is
   `src/main/java/com/chapmanjw/mcpserver/tools/<domain>/`.
2. Either add a new file (one-tool-per-file pattern, used by `server/*Tool.java`) or extend the
   existing per-domain `XxxTools.java` file as a `public static final class`.
3. Annotate with `@McpTool`:

   ```java
   @McpTool(
           name = "<domain>_<verb>",
           description = "One-sentence summary surfaced in tools/list.",
           minMinecraftVersion = "26.1.0",                       // optional
           requiredFabricModules = { "fabric-biome-api-v1" })    // optional
   public static final class Foo extends BaseTool { ... }
   ```

4. Extend `BaseTool`. Define a static JSON Schema via `Schemas.object().required(...).build()`.
5. Implement `execute(arguments, context)`. Submit all Minecraft API access via
   `onMainThread(context, ignored -> { ... })`.
6. Add a call into the adapter if the tool needs new functionality. If the adapter method doesn't
   exist yet, add it to `MinecraftAdapter` and implement in `MinecraftAdapterImpl`.
7. Register the tool in `tools/ToolRegistration.ALL_TOOL_CLASSES`.
8. Document the tool in `docs/tools.md`.
9. Add a unit or gametest scenario.

## Adapter implementation notes

The adapter follows two rules:

1. **Reads use direct API.** Pass `MinecraftServer` / `ServerLevel` / `Entity` through type-safe
   getters. Convert to DTOs before returning.
2. **Writes go through Brigadier (`/commandExecute`).** Build the vanilla command string and
   dispatch through the captured command source. This is ~10–100× slower per call but radically
   more stable across mapping and minor-version differences.

If a write tool genuinely needs direct API (bulk fills, perf-critical work), document the reason
in code comments and gate the fast path behind a config flag.

Where the API surface diverges between 1.21.11 and 26.1.x, use Stonecutter `//?` blocks:

```java
//? if mc >= "26.1.0" {
import net.minecraft.world.item.ItemStackTemplate;
ItemStackTemplate stack = ItemStackTemplate.of(ITEM);
//?} else {
/*import net.minecraft.item.ItemStack;
ItemStack stack = new ItemStack(ITEM);*/
//?}
```

## Threading model

Every Minecraft API method call MUST happen on the server main thread. The two safe entry points:

- `BaseTool.onMainThread(context, fn)` — for tool handlers.
- Fabric API event callbacks — they already fire on the main thread.

Reads of `level.getAllEntities()` (or any `ServerLevel` accessor) from any other thread are
undefined behavior. The harness will not catch them — review your own diffs.

## Tests

- **Unit tests** in `src/test/java/com/chapmanjw/mcpserver/`. Run via `./gradlew test`.
- **Gametests** under `src/test/java/com/chapmanjw/mcpserver/gametest/` (planned). Run via
  `./gradlew runGametest`. These boot a Fabric test world, exercise tools against it, and
  verify world state.
- New tools must have at least one test (unit or gametest).

## Commit messages

Conventional commits, lower-case subject:

```
add: level_get_biome_at tool with fabric-biome-api-v1 dependency
fix: rate limiter losing tokens at the second-boundary
docs: clarify host validation reject reason in security.md
```

## Pull requests

- Squash before review (small PRs preferred — under 400 lines diff is ideal).
- Ensure `./gradlew check` and `./gradlew chiseledBuild` succeed.
- Update `CHANGELOG.md` under `## [Unreleased]`.
- Update relevant docs (`docs/tools.md` is a frequent forgotten one).
- Use the PR template.

## Releasing

Releases are tag-driven. To cut a release:

1. Bump `mod.version` in `gradle.properties`.
2. Update `CHANGELOG.md` — move `## [Unreleased]` items into a new `## [<version>]` heading.
3. Commit and push.
4. Tag: `git tag v<version> && git push origin v<version>`.
5. `release.yml` builds, tests, and publishes to GitHub Releases.

Modrinth / CurseForge publication require org secrets (`MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`).
The corresponding workflow jobs are commented in `release.yml` until those are set up.
