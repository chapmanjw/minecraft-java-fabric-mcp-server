pluginManagement {
    repositories {
        // Stonecutter releases are published here.
        maven("https://maven.kikugie.dev/releases")
        // Fabric Loom is published here. Snapshots are required for Minecraft 26.1+
        // support, so include them explicitly rather than letting Gradle filter
        // them out when resolving "1.16-SNAPSHOT".
        maven("https://maven.fabricmc.net/") {
            // Fabric's maven hosts net.fabricmc and several subgroups (.unpick,
            // .fabric-loom, .yarn, .intermediary). Use a regex include so Loom's
            // transitives — like net.fabricmc.unpick:unpick — resolve from here.
            mavenContent {
                includeGroupByRegex("net\\.fabricmc(\\..*)?")
                includeGroup("fabric-loom")
            }
        }
        // NeoForged Maven mirrors a number of Mojang artifacts that Loom resolves.
        maven("https://maven.neoforged.net/releases")
        gradlePluginPortal()
        mavenCentral()
    }
    // Force the fabric-loom plugin resolution to pull directly from Fabric's
    // maven module coordinate. The plugin-portal marker for fabric-loom only
    // tracks stable releases (the latest being 1.16.2 at time of writing), but
    // Minecraft 26.1+ needs Loom 1.16-SNAPSHOT, which is published only on
    // Fabric's maven. useModule() bypasses the plugin marker and resolves the
    // artifact directly.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "fabric-loom" ||
                requested.id.id == "net.fabricmc.fabric-loom") {
                useModule("net.fabricmc:fabric-loom:${requested.version}")
            }
        }
    }
}

plugins {
    // Stonecutter drives the multi-version build (one source tree → N jars).
    // Pin to the 0.8.x line to avoid unexpected plugin upgrades breaking the matrix.
    id("dev.kikugie.stonecutter") version "0.8.3"
}

stonecutter {
    create(rootProject) {
        versions("1.21.11", "26.1.1", "26.1.2")

        vcsVersion = "26.1.2"

        // Each version subproject has its OWN build.gradle.kts (under versions/<ver>/).
        // This is required because the Fabric Loom plugin ID is version-dependent:
        //   * 1.21.x and earlier — `fabric-loom` (legacy, obfuscated MC)
        //   * 26.1+              — `net.fabricmc.fabric-loom` (LoomNoRemap, native names)
        // Putting plugin selection in a per-version build.gradle.kts is the only way to
        // pick a plugin ID inside the `plugins {}` block at the right configuration phase.
        // Shared logic lives in `mod-build.gradle.kts` and is applied via
        // `apply(from = ...)` at the bottom of each per-version script.
        //
        // The path here is relative to the version subproject's directory (versions/<v>/),
        // and Stonecutter prefixes it with `../../`, so the final resolved file is the
        // top-level versions/<v>/build.gradle.kts. See TreeBuilderImpl.createNode().
        mapBuilds { _, node -> "versions/${node.project}/build.gradle.kts" }
    }
}

rootProject.name = "mcp-server"
