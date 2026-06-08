// Per-version build script for Minecraft 1.21.11.
//
// 1.21.x ships obfuscated JARs, so we use the LEGACY Fabric Loom plugin
// (`fabric-loom`) which handles intermediary -> mojmap remap and exposes
// `modImplementation` / `loom.officialMojangMappings()`.
//
// Stonecutter routes the build for the `1.21.11` subproject to this file via
// `mapBuilds { _, node -> "versions/${node.project}/build.gradle.kts" }` in
// settings.gradle.kts. The shared logic (dependencies, processResources, jar,
// publishing, code quality) is in mod-build.gradle.kts at the repo root.

plugins {
    java
    `maven-publish`
    id("fabric-loom") version "1.16-SNAPSHOT"
    id("dev.kikugie.stonecutter")
    id("com.diffplug.spotless") version "7.0.2"
    id("checkstyle")
}

// Loom-specific configuration. We can use the typed `loom { }` accessor here
// because the plugin is declared in this script's `plugins { }` block, so
// kotlin-dsl generates accessors for it.
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
// in this script's `plugins { }` block. The shared script handles the rest.
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

// Wire up the rest of the build (dependencies, jar manifest, processResources, etc.).
// The shared script reads deps.mappings_type=mojmap from this subproject's
// gradle.properties and configures `mappings(loom.officialMojangMappings())`
// and `modImplementation` appropriately.
apply(from = rootProject.file("mod-build.gradle.kts"))
