# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.2] - 2026-08-20

### Fixed

- Detekt's `**/generated/**`/`**/build/**` exclude patterns no longer get silently overridden on
  AGP/KMP Android-target variant tasks (e.g. `detektAndroidDebug`). detekt-gradle-plugin's own
  lazy variant-registration callback reassigns `task.source` from the AGP variant's `sourceSets`
  after this plugin's own `fileTree(include/exclude)` assignment, and that AGP source set already
  treats the KSP output directory as a first-class source root — so KSP-generated code was being
  linted. Added a lazy, absolute-path-based `task.exclude { ... }` that survives the later
  `setSource()` override.

## [2.0.1] - 2026-08-19
* Switch to use Gradle commons library

## [2.0.0] - 2026-08-17

### Changed

- Reworked the plugin for lazy configuration and dependency wiring (breaking). Removed
  `rulesPluginJar`/`rulesPluginJars`, unified every external dependency into a single
  `from(...)` slot API (`ktlint.cli`, `detekt.<platform>.rules`), replaced scattered
  source-pattern properties with `sources { include/exclude/useDefaults }` blocks. See
  [MIGRATION.md](MIGRATION.md).
- Added a bundled default for `detekt.android.rules` — no longer requires an implicit
  `libs/detekt-rules-1.4.0.jar` pickup.

### Added

- `detekt.baseline` — optional baseline file to suppress pre-existing findings (e.g. for incremental
  adoption on legacy modules).
- `detekt.xmlReportEnabled` / `detekt.sarifReportEnabled` — opt-in emitters for XML and SARIF report
  formats per detekt task.
- `androidLint.enabled` — opt-in wiring to integrate Android Gradle Plugin lint checks into
  `pipelineCheck` and `prePushCheck` aggregate tasks (off by default: lint is slow).
- `generateDefaultDetektAndroidRulesJar` task — materializes bundled KODE Android detekt rules jar
  under `<root>/build/app-quality/detekt/rules/`.
- Full test coverage across all DSL/config surfaces, including a real Kotlin-DSL (`.gradle.kts`)
  consumer test, Kotlin Multiplatform module coverage, an `org.jetbrains.compose` (Compose
  Multiplatform) functional test, and a zero-config "real production shape" test mirroring the
  three current adopters.
- Documentation completion: accurate README examples, full backfilled `CHANGELOG.md`.

## [1.0.8] - 2026-04-09

- Updated ktlint to a newer version, plus additional dependencies.
- Added `README.md` with project info.

## [1.0.7] - 2026-03-26

- Added logic to register the `pipelineCheck` task.

## [1.0.6] - 2026-03-25

- Added logic to provide libraries from the version catalog.

## [1.0.5] - 2026-03-25

- Reverted provider usage for detekt tasks; removed non-cacheable logic.

## [1.0.3] - 2026-03-25

- Fixed configuration-cache issues and logger usage; reworked detekt configuration logic.
- Moved logger usage to task execution via build services.

## [1.0.2] - 2026-03-24

- Fixed ktlint check to use the correct logger.
- Fixed detekt ignored build types handling.
- Added sources configuration.

## [1.0.1] - 2026-03-24

- Initial tagged release.
- Added a JVM target fallback when no Kotlin tasks are present.
- Removed a duplicate core library dependency (reused from build-publish-core).
- Fixed ktlint and config handling.
