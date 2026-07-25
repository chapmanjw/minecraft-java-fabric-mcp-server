// Shared build logic for every Stonecutter version subproject.
//
// This script is applied via `apply(from = rootProject.file("mod-build.gradle.kts"))`
// at the bottom of each `versions/<ver>/build.gradle.kts`. The per-version script is
// responsible for:
//   * Declaring the right Fabric Loom plugin in its `plugins { }` block
//     (`fabric-loom` for 1.21.x, `net.fabricmc.fabric-loom` for 26.1+).
//   * Calling the `loom { }` extension to set up source sets, mods, and runs.
//   * Then `apply(from = ...)` THIS script to wire up shared dependencies,
//     processResources templating, jar manifest, Spotless, Checkstyle, and publishing.
//
// Two important constraints inform the structure here:
//
//   1. apply-from scripts can't declare a `plugins { }` block, and they don't have
//      Kotlin-DSL accessors for plugins applied to the root script. So we can't say
//      `loom { ... }` here — we use string-keyed `extensions.configure("loom")` if needed
//      (we don't, because we put the loom block in the per-version script where the typed
//      accessor IS available). We also need to pull plugin classes onto THIS script's
//      compile classpath via the `buildscript { }` block below so types like
//      SpotlessExtension and CheckstyleExtension can be referenced.
//
//   2. The 26.1+ Fabric Loom plugin (`net.fabricmc.fabric-loom`, a.k.a. LoomNoRemap)
//      sets `disableObfuscation = true`, which DELETES the `mappings` configuration
//      and the `modImplementation` / `modCompileOnly` / etc. configurations entirely.
//      So when `deps.mappings_type=mojmap_native`, we must NOT call `mappings(...)` and
//      we must use plain `implementation` instead of `modImplementation`. We do this
//      with `dependencies.add("configurationName", ...)` rather than typed accessors.

import java.time.LocalDate

// ---------------------------------------------------------------------------
// Identity (read from gradle.properties — keep gradle.properties as the source
// of truth, never hard-code these strings here). Properties are dot-separated in
// gradle.properties, so we read via property("mod.id") rather than relying on the
// Kotlin-DSL `by project` delegate (which expects camelCase property names).
// ---------------------------------------------------------------------------

val modId = property("mod.id") as String
val modGroup = property("mod.group") as String
val modVersion = property("mod.version") as String
val modName = property("mod.name") as String
val modDescription = property("mod.description") as String
val modAuthor = property("mod.author") as String
val modLicense = property("mod.license") as String
val modHomepage = property("mod.homepage") as String
val modSources = property("mod.sources") as String
val modIssues = property("mod.issues") as String

group = modGroup

// Per-version Minecraft / Loom / mappings coordinates. These come from
// versions/<ver>/gradle.properties when Stonecutter resolves the subproject.
val mcVersion = property("deps.minecraft") as String
val loaderVersion = property("deps.fabric_loader") as String
val fabricApiVersion = property("deps.fabric_api") as String
val mappingsType = property("deps.mappings_type") as String
// MCP wire-protocol version we implement. Update when adopting a newer revision.
val mcpProtocolVersion = "2025-06-18"

// Library versions come from the `libs` version catalog (gradle/libs.versions.toml), which
// Dependabot reads and edits directly. We resolve it through the VersionCatalogsExtension
// rather than the generated `libs.*` accessors: this is an apply-from script, and those
// accessors are only synthesised for real build scripts (the same limitation described in
// the header note about plugin accessors).
val libs = extensions
    .getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
    .named("libs")

fun catalogLib(alias: String): String =
    libs.findLibrary(alias).orElseThrow {
        GradleException("Version catalog 'libs' has no library alias '$alias'")
    }.get().toString()

// version of the form 0.1.0+1.21.11 — keeps every emitted jar uniquely versioned
// per Minecraft target so they coexist in a single GitHub Release.
version = "$modVersion+$mcVersion"
val javaVersion = (property("java.toolchain.version") as String).toInt()

// Register a Stonecutter boolean constant `mc_gte_26` so the //? if mc_gte_26
// blocks in Java sources resolve per subproject. Stonecutter `constants` are
// name -> boolean and are looked up via bare identifiers in `//?` expressions.
//
// We reach into the extension via reflection because the typed `stonecutter`
// accessor is only available where the plugin appears in a `plugins { }` block,
// and apply-from scripts can't import those accessors.
run {
    val ext = extensions.findByName("stonecutter") ?: return@run
    val constants = ext.javaClass.getMethod("getConstants").invoke(ext)
    val constPut = constants.javaClass.methods.firstOrNull {
        it.name == "put" &&
            it.parameterCount == 2 &&
            it.parameterTypes[0] == String::class.java &&
            it.parameterTypes[1] == java.lang.Boolean.TYPE
    }
    val mcGte26 = mcVersion.startsWith("26.") || mcVersion.startsWith("27.")
    constPut?.invoke(constants, "mc_gte_26", mcGte26)
}

// `base` is from the standard `java` plugin (applied by Loom transitively).
// Gradle appends `-<version>` to archivesName automatically, so we use just the mod id
// (the version already contains the MC suffix via "$modVersion+$mcVersion").
configure<BasePluginExtension> {
    archivesName.set("minecraft-fabric-mcp")
}

// ---------------------------------------------------------------------------
// Repositories
// ---------------------------------------------------------------------------

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://maven.parchmentmc.org/") { name = "ParchmentMC" }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
}

// ---------------------------------------------------------------------------
// Dependencies — Minecraft, mappings, Fabric loader/API, MCP SDK, Jackson.
//
// We use the string-keyed `add(configurationName, dep)` form for the loom-specific
// configurations so this script works under both legacy and NoRemap Loom. With
// LoomNoRemap:
//   * `mappings` configuration does not exist — must skip.
//   * `modImplementation` configuration does not exist — use plain `implementation`.
// ---------------------------------------------------------------------------

dependencies {
    add("minecraft", "com.mojang:minecraft:$mcVersion")

    when (mappingsType) {
        "mojmap" -> {
            // Legacy Loom only — call officialMojangMappings() via the loom extension
            // dynamically because the typed accessor isn't visible from apply-from scripts.
            // Use project.extensions.getByName explicitly — inside `dependencies { }` the
            // receiver is DependencyHandler, whose own `extensions` property is empty.
            val loomExt = project.extensions.getByName("loom")
            val officialMojang = loomExt.javaClass.getMethod("officialMojangMappings").invoke(loomExt)
            add("mappings", officialMojang!!)
        }
        "mojmap_native" -> {
            // 26.1+: LoomNoRemap is active. mappings configuration is intentionally absent;
            // the unobfuscated JAR is consumed as-is. Skip the `mappings` dependency entirely.
        }
        "yarn" -> {
            val yarnVersion = property("deps.yarn_mappings") as String
            add("mappings", "net.fabricmc:yarn:$yarnVersion:v2")
        }
        else -> throw GradleException(
            "Unknown deps.mappings_type='$mappingsType' for $mcVersion " +
                "(expected 'mojmap', 'mojmap_native', or 'yarn')"
        )
    }

    // Fabric loader / API. In legacy Loom, modImplementation triggers remapping; in
    // LoomNoRemap, that configuration doesn't exist and we use plain implementation
    // because the JAR is already in clear names.
    val modConfig = if (mappingsType == "mojmap_native") "implementation" else "modImplementation"
    add(modConfig, "net.fabricmc:fabric-loader:$loaderVersion")
    add(modConfig, "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // MCP wire protocol — JSON-RPC 2.0 + Streamable HTTP per modelcontextprotocol.io.
    // We implement the wire protocol directly against Jackson rather than depending
    // on io.modelcontextprotocol.sdk:mcp, because:
    //   1. The SDK's transport SPI is geared toward Servlet/WebFlux runtimes; we use
    //      JDK HttpServer, which would force a custom transport adapter anyway.
    //   2. The wire format is short and stable — avoiding the SDK keeps the mod jar
    //      ~1-2 MB smaller and removes a transitive update surface.
    //   3. The protocol/ package is the only place that knows about MCP semantics, so
    //      a future swap to the SDK is mechanical if the trade-offs change.
    // See docs/architecture.md for the full rationale.

    // Jackson — pinned and shaded into the mod jar so we don't share a version with
    // any other mod's bundled Jackson. The `include` configuration is provided by Loom
    // under both plugin IDs. Versions come from gradle/libs.versions.toml.
    listOf(
        catalogLib("jackson-databind"),
        catalogLib("jackson-core"),
        catalogLib("jackson-annotations")
    ).forEach { gav ->
        add("implementation", gav)
        add("include", gav)
    }

    // SLF4J is provided by Minecraft — compileOnly to keep it off the runtime classpath.
    add("compileOnly", catalogLib("slf4j-api"))

    // Testing
    add("testImplementation", catalogLib("junit-jupiter"))
    add("testImplementation", catalogLib("mockito-core"))
    add("testImplementation", catalogLib("mockito-junit-jupiter"))
    // Deliberately not from the catalog: its version is resolved by the junit-jupiter BOM,
    // and a version-less catalog entry stringifies to a trailing-colon coordinate.
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
}

// ---------------------------------------------------------------------------
// Gametest scaffolding lives under `src/gametest/` at the project root. It is NOT
// wired into a Gradle source set by default because Stonecutter's preprocessing
// and Loom's mod-detection don't compose cleanly with an extra source set in this
// project's per-version Stonecutter layout — registering the source set causes
// duplicate-class errors when Stonecutter pre-processes the gametest tree once
// per subproject.
//
// See docs/gametests.md for the activation walkthrough (essentially: register a
// `gametest` source set in the per-version build.gradle.kts and call
// `loom.runs.register("gametest") { ... }`).
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Java toolchain.
// ---------------------------------------------------------------------------

configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
    // Lossy warnings turned on; deprecation/serial off because Minecraft drags
    // those in transitively and the noise overwhelms real issues.
    options.compilerArgs.addAll(listOf("-Xlint:all,-deprecation,-serial,-processing"))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

// ---------------------------------------------------------------------------
// fabric.mod.json templating — substitutes ${mod_id}, ${mod_version}, etc.
// at processResources time. Keeps the json declarative and version-agnostic.
// ---------------------------------------------------------------------------

tasks.named<ProcessResources>("processResources") {
    inputs.property("mod_id", modId)
    inputs.property("mod_version", modVersion)
    inputs.property("mod_name", modName)
    inputs.property("mod_description", modDescription)
    inputs.property("mod_license", modLicense)
    inputs.property("mod_homepage", modHomepage)
    inputs.property("mod_sources", modSources)
    inputs.property("mod_issues", modIssues)
    inputs.property("mc_version", mcVersion)
    inputs.property("fabric_loader_version", loaderVersion)
    inputs.property("fabric_api_version", fabricApiVersion)

    // Pin the depends.minecraft constraint to the major.minor of the active subproject
    // so a 1.21.11 build refuses to load on 1.20.x or 26.1.x.
    val mcMajorMinor = mcVersion.substringBeforeLast('.')

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "mod_id" to modId,
                "mod_version" to modVersion,
                "mod_name" to modName,
                "mod_description" to modDescription,
                "mod_license" to modLicense,
                "mod_homepage" to modHomepage,
                "mod_sources" to modSources,
                "mod_issues" to modIssues,
                "mc_version" to mcVersion,
                "mc_constraint" to ">=$mcMajorMinor",
                "fabric_loader_version" to loaderVersion,
                "fabric_api_version" to fabricApiVersion,
                "java_version" to ">=$javaVersion"
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Jar manifest — embeds release metadata so `unzip -p mcp_server-*.jar META-INF/MANIFEST.MF`
// is informative for issue reporters.
// ---------------------------------------------------------------------------

// Whether the build uses legacy Loom (which registers a `remapJar` task to produce
// the consumable artifact) or LoomNoRemap (where the plain `jar` IS the consumable).
// Determined by mappings_type so we don't have to inspect tasks during configuration.
val usesLegacyLoom = mappingsType != "mojmap_native"

tasks.named<Jar>("jar") {
    from("LICENSE") { rename { "${it}_$modId" } }
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to modName,
                "Implementation-Version" to version,
                "Implementation-Vendor" to modAuthor,
                "Built-Date" to LocalDate.now().toString(),
                "Built-On-Minecraft" to mcVersion,
                "Built-On-Fabric-API" to fabricApiVersion,
                "Specification-Title" to "Model Context Protocol",
                "Specification-Version" to mcpProtocolVersion
            )
        )
    }
    if (usesLegacyLoom) {
        // Legacy Loom remaps the unremapped `-dev.jar` into `remapJar` for release. We
        // tag this one as "dev" so users can tell the two apart locally.
        archiveClassifier.set("dev")
    }
}

// Loom's remapJar is the artifact published / consumed by Minecraft. Make sure its
// archive name follows the same scheme as the unremapped jar. We use the
// AbstractArchiveTask supertype rather than Jar because the Jar class is loaded by
// a different classloader in the buildscript than the one Loom's RemapJarTask
// extends — a direct cast to Jar throws ClassCastException across the classloader split.
tasks.findByName("remapJar")?.let { remap ->
    (remap as org.gradle.api.tasks.bundling.AbstractArchiveTask).archiveClassifier.set("")
}

// ---------------------------------------------------------------------------
// Testing
// ---------------------------------------------------------------------------

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    // Run unit tests against a deterministic timezone / locale to avoid flaky
    // string comparisons in DTO serialization tests.
    systemProperty("user.timezone", "UTC")
    systemProperty("file.encoding", "UTF-8")
}

// ---------------------------------------------------------------------------
// Coverage — JaCoCo.
//
// We apply the plugin via apply(plugin = ...) rather than the typed `jacoco { }`
// extension because this is an apply-from script (no plugins { } block here).
// The coverage report intentionally excludes:
//   - com.chapmanjw.mcpserver.adapter.impl.** : depends on Minecraft runtime.
//   - com.chapmanjw.mcpserver.tools.**        : depends on Minecraft runtime.
//   - com.chapmanjw.mcpserver.McpServerMod    : Fabric entry point.
// These layers are integration-tested through the running mod, not via JUnit.
// ---------------------------------------------------------------------------
apply(plugin = "jacoco")

tasks.withType<JacocoReport>().configureEach {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/test/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
    }
    val excludedPatterns = listOf(
        // Layers that talk to Minecraft directly — integration tested through the
        // running mod, not via JUnit. Note the package is
        // `com.chapmanjw.minecraft.fabric.mcp.*`; older patterns referenced
        // `com.chapmanjw.mcpserver.*` and silently excluded nothing.
        "com/chapmanjw/minecraft/fabric/mcp/adapter/impl/**",
        "com/chapmanjw/minecraft/fabric/mcp/adapter/client/**",
        "com/chapmanjw/minecraft/fabric/mcp/tools/**",
        "com/chapmanjw/minecraft/fabric/mcp/McpServerMod*",
        "com/chapmanjw/minecraft/fabric/mcp/McpClientMod*"
    )
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(excludedPatterns) }
        })
    )
}

tasks.named<Test>("test") {
    finalizedBy(tasks.named("jacocoTestReport"))
}

// ---------------------------------------------------------------------------
// Code quality — Spotless + Checkstyle.
//
// The actual `spotless { ... }` and `checkstyle { ... }` blocks live in each
// per-version build.gradle.kts because the typed Kotlin DSL accessors for those
// plugins are only available where the plugin appears in the `plugins { }` block.
// Apply-from scripts (like this one) can't see those accessors.
//
// The shared bit — Checkstyle task reporting — is configured here via the
// untyped `tasks.withType` accessor, which IS available in apply-from scripts.
// ---------------------------------------------------------------------------

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// ---------------------------------------------------------------------------
// Publishing — `maven-publish` so downstream automation can mirror jars.
// ---------------------------------------------------------------------------

configure<PublishingExtension> {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "$modId-$mcVersion"
        }
    }
    repositories {
        // CI passes -Pmaven.local=... to point at a staging dir during release.
        // Real publishing happens through GitHub Releases / Modrinth / CurseForge
        // separately in release.yml.
        mavenLocal()
    }
}
