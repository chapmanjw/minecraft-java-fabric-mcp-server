// Stonecutter controller script — applied to the root project.
//
// Use `./gradlew chiseledBuild` to build every version. Use `./gradlew build`
// against the active subproject (controlled by `stonecutter.active` below or
// by `./gradlew "Reset active project" -Pversion=…`).

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2"

// Aggregate tasks that fan out across every version subproject. Stonecutter 0.8.x
// does not register these automatically, so we wire them up here.
tasks.register("chiseledBuild") {
    group = "build"
    description = "Builds every Stonecutter version subproject."
    dependsOn(stonecutter.versions.map { project(":${it.project}").path + ":build" })
}

tasks.register("chiseledTest") {
    group = "verification"
    description = "Runs the unit-test suite against every Stonecutter version subproject."
    dependsOn(stonecutter.versions.map { project(":${it.project}").path + ":test" })
}

tasks.register("chiseledCheck") {
    group = "verification"
    description = "Runs the full `check` task (lint + tests + access widener) against every Stonecutter version."
    dependsOn(stonecutter.versions.map { project(":${it.project}").path + ":check" })
}

tasks.register("chiseledAssemble") {
    group = "build"
    description = "Assembles the release jar for every Stonecutter version subproject."
    dependsOn(stonecutter.versions.map { project(":${it.project}").path + ":assemble" })
}
