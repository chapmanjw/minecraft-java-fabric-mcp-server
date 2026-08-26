// Per-version build script for Minecraft 26.1.1.
//
// 26.1+ ships UNOBFUSCATED JARs, so we use Fabric Loom's LoomNoRemap plugin
// (`net.fabricmc.fabric-loom`). LoomNoRemap sets `disableObfuscation = true`,
// which DELETES the `mappings`, `modImplementation`, `modCompileOnly`, and
// related configurations — the source consumes the clear-named JAR directly.
// See https://github.com/FabricMC/fabric-loom/issues/1541 for the rationale.
//
// Stonecutter routes the build for the `26.1.1` subproject to this file via
// `mapBuilds { _, node -> "versions/${node.project}/build.gradle.kts" }` in
// settings.gradle.kts. The shared logic (dependencies, processResources, jar,
// publishing, code quality) is in mod-build.gradle.kts at the repo root.

plugins {
    java
    `maven-publish`
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
    id("dev.kikugie.stonecutter")
    id("com.diffplug.spotless") version "8.10.0"
    id("checkstyle")
}

// Loom-specific configuration. LoomNoRemap exposes a reduced subset of the
// LoomGradleExtensionAPI — e.g. `splitEnvironmentSourceSets()` is still present
// but `officialMojangMappings()` is intentionally unavailable.
loom {
    splitEnvironmentSourceSets()

    mods {
        register(property("mod.id") as String) {
            sourceSet("main")
            // Client-only inspection tools (view_capture, sense_*, client_status) live in the
            // split client source set; including it here puts those classes in the mod jar.
            sourceSet("client")
        }
    }

    runs {
        named("server") {
            runDir("run/server")
        }
    }
}

// Spotless and Checkstyle are configured here (rather than in mod-build.gradle.kts)
// because their typed DSL accessors are only available where the plugin appears
// in this script's `plugins { }` block.
spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.22.0")
        licenseHeaderFile(rootProject.file("config/spotless/license-header.txt"))
        removeUnusedImports()
        importOrder("java", "javax", "")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "stonecutter.gradle.kts")
        endWithNewline()
        trimTrailingWhitespace()
    }
    format("misc") {
        target("*.md", ".gitignore", ".editorconfig")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

checkstyle {
    toolVersion = "10.20.2"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    configProperties["checkstyle.suppressions.file"] =
        rootProject.file("config/checkstyle/suppressions.xml")
    maxWarnings = 0
}

// Wire up the rest of the build. The shared script reads
// deps.mappings_type=mojmap_native from this subproject's gradle.properties and
// skips the `mappings(...)` declaration + uses plain `implementation` for the
// Fabric loader / API (since `modImplementation` doesn't exist under LoomNoRemap).
apply(from = rootProject.file("mod-build.gradle.kts"))
