# Gametests

The mod ships **gametest scaffolding** under `src/gametest/java/`. These classes boot a real
Fabric server with a tiny structure loaded, invoke MCP tools through the live dispatcher, and
assert that Minecraft state matches. They're not wired into a Gradle source set by default —
the next section explains why, and how to opt in.

## Status

In v0.2.0 the gametest tree is **scaffolding only** — the Java classes are written and
correct, but they're not compiled or executed during `./gradlew chiseledBuild` or
`./gradlew chiseledTest`. The decision is deliberate:

- Stonecutter pre-processes any registered source set per version subproject, including a
  `gametest` one if we declared it. The pre-processed copies of the gametest classes
  collide with the originals on the compileClasspath, producing duplicate-class errors.
- The cleanest workaround would be to either (a) only register the gametest source set in
  the `vcsVersion` subproject, (b) materialize a wholly separate `versions/<ver>/src/gametest/`
  tree per subproject, or (c) wait for upstream Stonecutter to grow source-set-aware
  preprocessing. None of those are zero-cost for v0.2.0.

The classes themselves compile under the right setup (verified locally with a single-version
project) and exercise the canonical MCP dispatch path against a live server.

## Activating gametests in a downstream fork

In your fork's `versions/<ver>/build.gradle.kts`, after `apply(from = rootProject.file("mod-build.gradle.kts"))`:

```kotlin
sourceSets {
    register("gametest") {
        java.srcDir(rootProject.file("src/gametest/java"))
        resources.srcDir(rootProject.file("src/gametest/resources"))
        compileClasspath += sourceSets["main"].output + sourceSets["main"].compileClasspath
        runtimeClasspath += sourceSets["main"].output + sourceSets["main"].runtimeClasspath
    }
}

loom {
    runs {
        register("gametest") {
            inherit(named("server").get())
            source(sourceSets["gametest"])
            property("fabric.gametest")
        }
    }
}
```

Then `./gradlew :<ver>:runGametest` boots the server, loads the gametest classes, and runs
every `@GameTest`-annotated method.

## What's covered

| Class                     | Domain     | Scenarios                                                            |
| ------------------------- | ---------- | -------------------------------------------------------------------- |
| `ServerToolsGameTest`     | server     | `server_get_status`, `server_set_motd` → `server_get_motd` round-trip |
| `LevelToolsGameTest`      | level      | `level_set_time` round-trip; `level_set_weather` round-trip          |
| `BlockToolsGameTest`      | block      | `block_set_state` places a diamond block; `block_get_state` reads it |
| `EntityToolsGameTest`     | entity     | `entity_summon` creates an armor stand at a specific position        |
| `ScoreboardToolsGameTest` | scoreboard | `scoreboard_add_objective` + `scoreboard_remove_objective`           |
| `EventsToolsGameTest`     | events     | Subscribe → synthesize event → poll → unsubscribe                    |

Each scenario uses `GametestHarness.bootstrap(server)`, which wires a real
`MinecraftAdapter`, `MinecraftMainThreadExecutor`, `EventBus`, and `ToolRegistry` around
the running server — i.e. the exact same wiring `McpServerMod.onServerStarting` does. The
only thing skipped is the HTTP transport; tests invoke the dispatcher directly with
synthetic JSON-RPC requests.

## Writing a new gametest

1. Add a class in `src/gametest/java/com/chapmanjw/mcpserver/gametest/`.
2. Implement `FabricGameTest`.
3. Add `@GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")` to each test method.
4. Receive a `GameTestHelper` parameter. Use `helper.getLevel()` for direct world access
   and `helper.absolutePos(BlockPos)` to translate relative coords to absolute.
5. End every method with `helper.succeed()` on success; throw `AssertionError` (or use
   `helper.assertTrue`) on failure.
6. Register the class in `src/gametest/resources/fabric.mod.json` under the
   `fabric-gametest` entrypoint list.

The `minecraft_fabric_mcp:gametest/empty` template is a 3×3×3 stone box at world origin; it gives
enough room to place blocks and spawn small entities. Add new structure templates under
`src/gametest/resources/data/minecraft_fabric_mcp/gametest/structure/`.
