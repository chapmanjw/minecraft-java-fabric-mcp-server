// Per-version build script for Minecraft 26.1.2.
// See versions/26.1.1/build.gradle.kts for the full explanation of why 26.1+
// uses the `net.fabricmc.fabric-loom` (LoomNoRemap) plugin instead of the legacy
// `fabric-loom` plugin.

plugins {
    java
    `maven-publish`
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
    id("dev.kikugie.stonecutter")
    id("com.diffplug.spotless") version "7.0.2"
    id("checkstyle")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register(property("mod.id") as String) {
            sourceSet("main")
        }
    }

    runs {
        named("server") {
            runDir("run/server")
        }
    }
}

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

apply(from = rootProject.file("mod-build.gradle.kts"))
