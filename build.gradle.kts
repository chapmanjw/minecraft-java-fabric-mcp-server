// Root project build script. Stonecutter dispatches actual builds to the per-version
// subprojects under versions/<ver>/, each of which has its own build.gradle.kts with
// the version-appropriate Fabric Loom plugin.
//
// This root script intentionally does NOT declare a Loom plugin — see
// settings.gradle.kts for the routing setup and mod-build.gradle.kts for the
// shared build logic.

plugins {
    // Stonecutter must be on the classpath of the root project so the
    // chiseledBuild task wiring (created by stonecutter.gradle.kts) can be applied.
    id("dev.kikugie.stonecutter")
}

// Nothing to build at the root. All build outputs come from the per-version
// subprojects under versions/<ver>/.
