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

### Optional `libs.versions.toml` entries

The plugin looks up these aliases in the `libs` catalog. None are required — any alias
missing (or the catalog itself missing) falls back to the plugin's bundled default version:

```toml
[versions]
detekt = "1.23.8"
ktlintCli = "1.8.0"
detektComposeRules = "1.4.0"

[libraries]
ktlint-cli = { module = "com.pinterest.ktlint:ktlint-cli", version.ref = "ktlintCli" }
detekt-formatting = { module = "io.gitlab.arturbosch.detekt:detekt-formatting", version.ref = "detekt" }
detekt-compose-rules = { module = "ru.kode:detekt-rules-compose", version.ref = "detektComposeRules" }
```

`detekt-compose-rules` is required only for modules using Compose Detekt rules.

Catalog lookups are lazy: a missing alias fails the first task that needs it (with an
explanatory message), not the plugin apply, so unrelated tasks keep working.

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
    id("ru.kode.android.app-quality.foundation") version "2.0.0"
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

- `gitHooksSetup`: runs `git config core.hooksPath <path>` (default `<root>/.githooks`);
  skipped automatically when the root project is not a git repository, or when
  `gitHooksEnabled` is set to `false`
- `ktlintCheck`: runs ktlint checks for Kotlin sources (up-to-date aware: skipped when
  sources and config did not change)
- `ktlintFormat`: runs ktlint auto-format for Kotlin sources
- `pipelineCheck`: depends on `gitHooksSetup`, `ktlintCheck`, `detektCheck` (if eligible modules exist), and
  `androidLintCheck` (if `androidLint.enabled` is `true`)
- `prePushCheck`: depends on `gitHooksSetup`, `ktlintFormat`, `detektCheck` (if eligible modules exist), and
  `androidLintCheck` (if `androidLint.enabled` is `true`)
- `androidLintCheck`: runs Android Gradle Plugin lint checks; skipped unless `androidLint.enabled` is `true`
- `printRequiredGradleJvmargs`: prints the current Gradle JVM input arguments
- `generateDefaultDetektKotlinConfig` / `...AndroidConfig` / `...ComposeConfig` / `...AndroidRulesJar` /
  `generateDefaultKtlintEditorconfig`: materialize bundled default configs and resources into
  `<root>/build/app-quality/`; run automatically only when a default is actually used. The rules jar
  (`generateDefaultDetektAndroidRulesJar`) contains the bundled KODE Android detekt ruleset and is placed
  under `<root>/build/app-quality/detekt/rules/`

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
    gitHooksEnabled.set(true) // set false to opt out of git hooks setup entirely

    ktlint {
        projectConfig.set(rootProject.layout.projectDirectory.file(".editorconfig"))
        sources {
            include.set(listOf("**/src/*/kotlin/**/*.kts"))
            exclude.set(listOf("**/build-logic/**"))
            // Also available as vararg sugar: include("**/src/*/kotlin/**/*.kts"); exclude("**/build-logic/**")
        }
    }

    detekt {
        ignoredBuildTypes.set(listOf("release", "internal", "external", "demo"))
        sources {
            include.set(listOf("src/custom/kotlin"))
            exclude.set(listOf("tmpGenerated"))
        }
        typeResolution.set(false)
        // Only the filename is used — it's re-resolved per subproject, so this is safe to set
        // once here even when app-quality-plugin is applied at the root only.
        baseline.set(layout.projectDirectory.file("detekt-baseline.xml"))
        xmlReportEnabled.set(true)
        sarifReportEnabled.set(false)

        kotlin {
            projectConfig.set(layout.projectDirectory.file("detekt-kotlin-config.yml"))
            rules {
                from(rootProject.layout.projectDirectory.file("libs/detekt-rules-1.4.0.jar"))
            }
        }

        android {
            projectConfig.set(layout.projectDirectory.file("detekt-android-config.yml"))
        }

        compose {
            projectConfig.set(layout.projectDirectory.file("detekt-compose-config.yml"))
        }
    }

    androidLint {
        enabled.set(true)
    }
}
```

### Defaults

| Property | Default |
| --- | --- |
| `verboseLogging` | `false` |
| `jvmTarget` | `JVM_17` |
| `gitHooks` | `<root>/.githooks` |
| `gitHooksEnabled` | `true` |
| `ktlint.sources.include` | `["**/src/*/java/**/*.kt", "**/src/*/kotlin/**/*.kt"]` (while `useDefaults` is `true`) |
| `ktlint.sources.exclude` | `["**/build/**", "**/generated/**", "**/templates/**", "**/src/test/**", "**/src/androidTest/**", "**/src/commonTest/**", "templates/**", "**/schema/**/*.kt"]` (while `useDefaults` is `true`) |
| `detekt.ignoredBuildTypes` | `["release", "internal", "external", "demo"]` |
| `detekt.sources.include` | per-platform Kotlin/Java source dirs (while `useDefaults` is `true`) |
| `detekt.sources.exclude` | `[]` |
| `detekt.typeResolution` | `false` |
| `detekt.baseline` | unset (no baseline); when set, resolved per-subproject by filename — safe to configure once regardless of where the plugin is applied |
| `detekt.xmlReportEnabled` | `false` |
| `detekt.sarifReportEnabled` | `false` |
| `androidLint.enabled` | `false` |
| `ktlint.cli` | `libs.ktlint-cli`, falling back to the plugin's own baked-in `com.pinterest.ktlint:ktlint-cli` coordinate if no matching catalog alias exists (while `useDefaults` is `true`) |
| `detekt.kotlin.rules` | `libs.detekt-formatting`, falling back to the plugin's own baked-in `io.gitlab.arturbosch.detekt:detekt-formatting` coordinate if no matching catalog alias exists (while `useDefaults` is `true`) |
| `detekt.android.rules` | the plugin's bundled KODE Android rules jar (not published anywhere externally — see [MIGRATION.md](MIGRATION.md)) (while `useDefaults` is `true`) |
| `detekt.compose.rules` | `libs.detekt-compose-rules`, falling back to the plugin's own baked-in `ru.kode:detekt-rules-compose` coordinate if no matching catalog alias exists (while `useDefaults` is `true`) |

### Configuring dependencies

Every external dependency of the plugin lives in a uniform slot (`ktlint.cli`,
`detekt.<platform>.rules`) configurable from ANY source through one `from(...)` API —
version-catalog accessors, string coordinates (e.g. your own published rule sets), or jar
files. Additions always stack ON TOP of the slot's default; disable the default with
`useDefaults.set(false)`.

For `ktlint.cli`/`detekt.kotlin.rules`/`detekt.compose.rules`, a matching alias in your own
`libs` catalog (if present) always wins; the plugin's baked-in coordinate is only a fallback,
so the plugin works with zero catalog setup too. `detekt.android.rules` has no catalog-alias
option at all — its default is bundled directly in the plugin (the jar isn't published to any
Maven repo). See [MIGRATION.md](MIGRATION.md) for upgrade notes.

```kotlin
appQualityFoundation {
    ktlint.cli {
        from(deps.ktlint.cli)                                // typed accessor from any catalog
        // from("com.pinterest.ktlint:ktlint-cli:1.8.0")     // or coordinates
        // from(fileTree("tools/ktlint") { include("*.jar") }) // or checked-in jars
        useDefaults.set(false)                               // drop the `libs` catalog default
    }
    detekt.kotlin.rules {
        from(files("libs/detekt-rules-1.4.0.jar"))           // stacks on detekt-formatting
    }
    detekt.compose.rules {
        from("ru.kode:detekt-rules-compose:1.4.0")           // published custom rules
        useDefaults.set(false)                               // replace the default entirely
    }
}
```

A configured-but-missing file in any slot fails the build with an explanatory message
naming the slot.

All configuration blocks (`ktlint { }`, `detekt { }`, `detekt.kotlin { }`, `cli { }`,
`rules { }`) have both `Action` and Groovy `Closure` overloads, so the same block syntax
works identically in `build.gradle.kts` and Groovy `build.gradle` scripts.

### Config file discovery and fallback

Detekt configs are resolved and merged **per module**: each module independently walks the
priority chain for every platform layer that applies to it (see Module Coverage below):

1. Extension override — set once at the root, forces that file for ALL modules
2. Module-local file — e.g. `<module>/detekt-kotlin-config.yml`, lets a module customize
   its own rules; other modules are unaffected
3. Bundled default — used by any module without an override or a local file

Only the *storage location* of the bundled defaults is root-level: since their content is
identical for every module, they are generated once under `<root>/build/app-quality/` by
dedicated tasks instead of being copied into every module. Nothing is written at
configuration time, so the configuration cache stays reusable and creating a module file
later is picked up correctly.

- Ktlint (runs once at the root over all modules, so its whole chain is root-level):
  - Extension override: `ktlint.projectConfig`
  - Project file lookup: `<root>/.editorconfig`
  - Bundled default (generated): `<root>/build/app-quality/ktlint/.editorconfig`
- Detekt Kotlin (per module):
  - Extension override: `detekt.kotlin.projectConfig`
  - Module file lookup: `<module>/detekt-kotlin-config.yml`
  - Bundled default (generated): `<root>/build/app-quality/detekt/kotlin-config.yml`
- Detekt Android (per module):
  - Extension override: `detekt.android.projectConfig`
  - Module file lookup: `<module>/detekt-android-config.yml`
  - Bundled default (generated): `<root>/build/app-quality/detekt/android-config.yml`
- Detekt Compose (per module):
  - Extension override: `detekt.compose.projectConfig`
  - Module file lookup: `<module>/detekt-compose-config.yml`
  - Bundled default (generated): `<root>/build/app-quality/detekt/compose-config.yml`

`detekt.kotlin.rules`, `detekt.compose.rules`, and `ktlint.cli` fall back to a baked-in
coordinate default when no matching `libs` catalog alias exists (see Defaults above).
`detekt.android.rules` has its own bundled-jar default, since it isn't published anywhere
externally. A configured-but-missing file in any slot fails the build with an explanatory
message naming the slot (see "Configuring dependencies" above).

## Module Coverage

Detekt is applied only to subprojects that use a matching plugin (plain Java modules are
left untouched). Config layers merged per module:

- Kotlin config — `org.jetbrains.kotlin.jvm`, `org.jetbrains.kotlin.multiplatform`,
  `org.jetbrains.kotlin.android`, `com.android.application`, `com.android.library`
- Android config (additionally) — `org.jetbrains.kotlin.android`,
  `com.android.application`, `com.android.library`
- Compose config (additionally) — `org.jetbrains.compose`,
  `org.jetbrains.kotlin.plugin.compose`

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
