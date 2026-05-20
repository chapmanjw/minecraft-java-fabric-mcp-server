# Build system

This document explains how the multi-version build is wired. Most contributors won't need any
of this — the `./gradlew chiseledBuild` happy path Just Works. Read this when:

- You need to bump a Minecraft, Fabric Loader, or Fabric API version.
- A new Minecraft major release is out and you're adding it to the matrix.
- The build is failing in a way the regular Fabric / Loom docs don't explain.

## File map

```
.
├── build.gradle.kts                # ★ Minimal root script. Applies Stonecutter only.
├── stonecutter.gradle.kts          # ★ Stonecutter controller — `stonecutter active` + chiseledBuild aggregate task.
├── settings.gradle.kts             # ★ pluginManagement (Loom snapshot resolution) + Stonecutter version registration.
├── mod-build.gradle.kts            # ★ Shared build logic (dependencies, jar, processResources, tests, publishing).
├── gradle.properties               # ★ JVM args, daemon settings, mod identity, default deps, JDK locator config.
└── versions/
    ├── 1.21.11/
    │   ├── build.gradle.kts        # ★ Per-version: declares `id("fabric-loom")` + applies mod-build.gradle.kts.
    │   └── gradle.properties       #   Per-version: deps.minecraft, deps.fabric_loader, deps.fabric_api,
    │                                #               deps.mappings_type=mojmap, java.toolchain.version=21.
    ├── 26.1.1/build.gradle.kts     # ★ Per-version: declares `id("net.fabricmc.fabric-loom")`.
    ├── 26.1.1/gradle.properties    #   deps.mappings_type=mojmap_native, java.toolchain.version=25.
    ├── 26.1.2/build.gradle.kts
    └── 26.1.2/gradle.properties
```

Files marked `★` are the load-bearing ones for the build itself.

## The plugin ID problem

Fabric Loom 1.16+ ships **two distinct plugin IDs** from the same JAR:

| Plugin ID                  | Class                         | DSL surface                                       | Use for             |
| -------------------------- | ----------------------------- | ------------------------------------------------- | ------------------- |
| `fabric-loom`              | `LoomGradlePlugin`            | `mappings`, `modImplementation`, `loom.officialMojangMappings()` | Obfuscated MC (≤ 1.21.x) |
| `net.fabricmc.fabric-loom` | `LoomNoRemapGradlePlugin`     | (no `mappings`, no `modImplementation`)           | Unobfuscated MC (26.1+)  |

Per [fabric-loom#1541](https://github.com/FabricMC/fabric-loom/issues/1541) and the
[26.1 porting guide](https://docs.fabricmc.net/26.1/develop/porting/#build-script), 26.1+ MUST
use the new plugin ID — the legacy ID hard-fails with `Failed to find official mojang mappings
for 26.1.X` because Mojang stopped publishing separate mappings files when the JAR became
unobfuscated.

Because the `plugins {}` block in a Kotlin DSL build script is **evaluated before any of the
script body runs** and Kotlin-DSL accessors are generated from the plugins declared there, we
can't make the plugin ID conditional inside one shared script. Each Stonecutter version
subproject therefore has its own `versions/<ver>/build.gradle.kts` that picks the right plugin
ID in its own `plugins {}` block.

## Why no Stonecutter `centralScript`?

Stonecutter's `centralScript` mechanism is designed to share one `build.gradle.kts` across
every version subproject. That works well when version-specific bits can be expressed via
property reads inside the script. It doesn't accommodate per-version plugin ID selection,
because plugin IDs live in the `plugins {}` block — which is resolved before any property
reads happen.

So we use `mapBuilds { _, node -> "versions/${node.project}/build.gradle.kts" }` in
`settings.gradle.kts` to route each subproject to its own per-version build script, and we
share the rest of the logic via `apply(from = rootProject.file("mod-build.gradle.kts"))` from
each per-version script.

## Stonecutter source-level conditionals

`MinecraftAdapterImpl.java` and other source files with version-divergent code use Stonecutter
preprocessor blocks:

```java
//? if mc_gte_26 {
level.getOverworldClockTime();
//?} else {
/*level.getDayTime();*/
//?}
```

The `mc_gte_26` boolean constant is defined in `mod-build.gradle.kts` and made available to
Stonecutter's evaluator:

```kotlin
stonecutter.constants.put("mc_gte_26", mcVersion.startsWith("26.") || mcVersion.startsWith("27."))
```

Stonecutter pre-processes the `src/main/java` tree per subproject, materializing the active
branch under `build/generated/stonecutter/`. Each subproject's `compileJava` task reads from
the preprocessed copy.

Stonecutter does **not** pre-process `build.gradle.kts` files (the version-routing in
`settings.gradle.kts` does that job instead).

## JDK toolchains

| Target  | Compile toolchain | Gradle daemon JDK | Why                                                                     |
| ------- | ----------------- | ----------------- | ----------------------------------------------------------------------- |
| 1.21.11 | JDK 21            | JDK 21+           | 1.21.x Minecraft targets Java 21 bytecode.                              |
| 26.1.x  | JDK 25            | JDK 25            | 26.1.x Minecraft targets Java 25; Loom enforces the daemon match too.  |

`gradle.properties` declares `org.gradle.java.installations.fromEnv=JDK_21,JDK_25,JAVA_HOME_21_X64,JAVA_HOME_25_X64`,
which means Gradle's toolchain locator picks up JDKs exposed via these env vars. On CI,
`actions/setup-java` sets `JAVA_HOME_21_X64` / `JAVA_HOME_25_X64`; locally, Gradle's default
auto-detection finds Corretto installs under `C:\Program Files\Amazon Corretto\`.

For local development on a host that only has one JDK, override with `-Porg.gradle.java.installations.paths=…`
on the command line.

## Loom snapshot resolution

`settings.gradle.kts` does TWO things specific to Loom:

1. Adds Fabric's maven (`https://maven.fabricmc.net/`) with a regex group include so
   `net.fabricmc.unpick` and other Loom transitives resolve.
2. Uses a `pluginManagement.resolutionStrategy.eachPlugin` block to map `fabric-loom` and
   `net.fabricmc.fabric-loom` to the `net.fabricmc:fabric-loom` Maven module coordinate.
   Without this, Gradle's plugin portal resolves `1.16-SNAPSHOT` to the stable `1.16.2`
   release, which lacks the unobfuscated-MC support 26.1 needs.

## Adding a new Minecraft version

1. Add a new entry to `settings.gradle.kts`: `versions("X.Y.Z", ...)`.
2. Create `versions/X.Y.Z/gradle.properties`:
   ```properties
   deps.minecraft=X.Y.Z
   deps.fabric_loader=…           # check https://meta.fabricmc.net/v2/versions/loader
   deps.fabric_api=…+X.Y.Z        # check https://meta.fabricmc.net/v2/versions/yarn
   deps.mappings_type=mojmap      # or mojmap_native for 26.1+
   deps.loom=1.16-SNAPSHOT
   java.toolchain.version=…       # whatever Minecraft of this version targets
   ```
3. Create `versions/X.Y.Z/build.gradle.kts` by copying the closest existing one and adjusting
   the plugin ID and any version-conditional sections.
4. If the new version introduces API changes, update the affected `src/main/java/...` files
   with Stonecutter `//? if mc_gte_X { … } //?} else { /*…*/ //?}` blocks.
5. Update the matrix in `.github/workflows/build.yml`.
6. Update `docs/version-compatibility.md` and this document.
7. Run `./gradlew chiseledBuild` and fix any breakage.

## Gotchas

- **Don't put plugin DSL config in `mod-build.gradle.kts`.** Apply-from scripts don't have
  Kotlin-DSL accessors for plugins applied at the root. Move that config to each per-version
  `versions/<ver>/build.gradle.kts` instead. Currently `spotless { … }`, `checkstyle { … }`,
  and `loom { … }` live there for this reason.
- **Stonecutter cache lock files.** If a build crashes mid-Stonecutter, Loom may leave a stale
  cache lock file (`...loom_cache/lock`). The next build prints `Previous process has disowned
  the lock due to abrupt termination`; that's harmless.
- **The Loom plugin prints `Fabric Loom: 1.16.2`** for both ID variants (the stable and snapshot
  hash-identical). The actual variant in use is determined by which Plugin class Gradle applied,
  not by the version string in the banner.
