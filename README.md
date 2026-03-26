# Android Quality Plugin

`ru.kode.android.app-quality.foundation` is a Gradle plugin for Android/Kotlin repositories that centralizes static analysis and formatting checks.

It configures Detekt for eligible modules, runs `ktlint` through CLI, and provides aggregate verification tasks for local development and CI.

## Features

- Registers aggregate quality tasks: `pipelineCheck` and `prePushCheck`
- Registers `ktlintCheck` and `ktlintFormat` tasks at the root project
- Configures Detekt in subprojects and merges platform-specific Detekt configs
- Sets Git hooks path via `gitHooksSetup`
- Supports configurable logging and Detekt JVM target
- Provides bundled default config files when project-level files are missing

## Requirements

- Java 17
- Gradle 9.x (this repository uses wrapper `9.4.0`)
- Version catalog named `libs`
- If the plugin is applied directly to an Android module project: Android Gradle Plugin `7.4.0+` and `com.android.application`

### Required `libs.versions.toml` entries

The plugin looks up these aliases in the `libs` catalog:

```toml
[versions]
detekt = "1.23.8"
ktlintCli = "0.46.0"
detektComposeRules = "1.2.2"

[libraries]
ktlint-cli = { module = "com.pinterest:ktlint", version.ref = "ktlintCli" }
detekt-formatting = { module = "io.gitlab.arturbosch.detekt:detekt-formatting", version.ref = "detekt" }
detekt-compose-rules = { module = "ru.kode:detekt-rules-compose", version.ref = "detektComposeRules" }
```

`detekt-compose-rules` is required only for modules using Compose Detekt rules.

## Installation

### Plugin Portal

In `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
```

In root `build.gradle.kts`:

```kotlin
plugins {
    id("ru.kode.android.app-quality.foundation") version "1.0.7"
}
```

Use the latest published version for your project.

### Local development with `mavenLocal`

Publish plugin artifacts locally:

```bash
./gradlew --project-dir plugin-build publishToMavenLocal
```

Then ensure your consumer project has `mavenLocal()` in `pluginManagement.repositories`, and apply:

```kotlin
plugins {
    id("ru.kode.android.app-quality.foundation") version "<local-version>"
}
```

## Quick Start

Run the main quality pipeline:

```bash
./gradlew pipelineCheck
```

Pre-push formatting + static analysis:

```bash
./gradlew prePushCheck
```

## Tasks

- `gitHooksSetup`: runs `git config core.hooksPath <path>` (default `<root>/.githooks`)
- `ktlintCheck`: runs ktlint checks for Kotlin sources
- `ktlintFormat`: runs ktlint auto-format for Kotlin sources
- `pipelineCheck`: depends on `gitHooksSetup`, `ktlintCheck`, and eligible Detekt tasks
- `prePushCheck`: depends on `gitHooksSetup`, `ktlintFormat`, and eligible Detekt tasks
- `printRequiredGradleJvmargs`: prints the current Gradle JVM input arguments

## Configuration

Extension name:

```kotlin
appQualityFoundation { ... }
```

Example:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

appQualityFoundation {
    verboseLogging.set(false)
    jvmTarget.set(JvmTarget.JVM_17)
    gitHooks.set(rootProject.layout.projectDirectory.file(".githooks"))

    ktlint {
        projectConfig.set(rootProject.layout.projectDirectory.file(".editorconfig"))
        additionalSourcePatterns.set(listOf("**/src/*/kotlin/**/*.kts"))
        additionalIgnoredSourcePatterns.set(listOf("!**/build-logic/**"))
    }

    detekt {
        ignoredBuildTypes.set(listOf("release", "internal", "external", "demo"))
        additionallyExcludedPaths.set(listOf("tmpGenerated"))
        additionalSourcePaths.set(listOf("src/custom/kotlin"))
        typeResolution.set(false)

        kotlin {
            projectConfig.set(layout.projectDirectory.file("detekt-kotlin-config.yml"))
            rulesPluginJar.set(rootProject.layout.projectDirectory.file("libs/detekt-rules-1.4.0.jar"))
        }

        android {
            projectConfig.set(layout.projectDirectory.file("detekt-android-config.yml"))
        }

        compose {
            projectConfig.set(layout.projectDirectory.file("detekt-compose-config.yml"))
        }
    }
}
```

### Defaults

| Property | Default |
| --- | --- |
| `verboseLogging` | `false` |
| `jvmTarget` | `JVM_17` |
| `gitHooks` | `<root>/.githooks` |
| `ktlint.additionalSourcePatterns` | `[]` |
| `ktlint.additionalIgnoredSourcePatterns` | `[]` |
| `detekt.ignoredBuildTypes` | `["release", "internal", "external", "demo"]` |
| `detekt.additionallyExcludedPaths` | `[]` |
| `detekt.additionalSourcePaths` | `[]` |
| `detekt.typeResolution` | `false` |
| `detekt.kotlin.rulesPluginJar` | `<root>/libs/detekt-rules-1.4.0.jar` |
| `detekt.kotlin.rulesLibraries` | `libs.detekt-formatting` |
| `detekt.compose.rulesLibraries` | `libs.detekt-compose-rules` |

### Config file discovery and fallback

If project config files are missing, the plugin uses bundled defaults copied into build directories:

- Ktlint:
  - Project file lookup: `<root>/.editorconfig` (or configured path)
  - Fallback: `<root>/build/ktlint/.editorconfig`
- Detekt Kotlin:
  - Project file lookup: `<module>/detekt-kotlin-config.yml`
  - Fallback: `<module>/build/detekt/kotlin-config.yml`
- Detekt Android:
  - Project file lookup: `<module>/detekt-android-config.yml`
  - Fallback: `<module>/build/detekt/android-config.yml`
- Detekt Compose:
  - Project file lookup: `<module>/detekt-compose-config.yml`
  - Fallback: `<module>/build/detekt/compose-config.yml`

## Module Coverage

Detekt configuration is applied for subprojects that use any of:

- `org.jetbrains.kotlin.jvm`
- `org.jetbrains.kotlin.multiplatform`
- `org.jetbrains.kotlin.android`
- `com.android.application`
- `com.android.library`
- `org.jetbrains.compose`
- `org.jetbrains.kotlin.plugin.compose`

## Development in This Repository

- Full checks: `./gradlew preMerge`
- Plugin checks only: `./gradlew --project-dir plugin-build preMerge`
- Test suite: `./gradlew --project-dir plugin-test test`
- Example app quality run: `./gradlew --project-dir example-project pipelineCheck`

## Publishing (Repository Maintainers)

Set environment variables:

- `GRADLE_PUBLISH_KEY`
- `GRADLE_PUBLISH_SECRET`

Then publish:

```bash
./gradlew --project-dir plugin-build setupPluginUploadFromEnvironment publishPlugins
```

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
