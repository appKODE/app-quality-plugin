# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.1.0] - 2026-09-29

### Fixed

- `detekt.typeResolution` means the same on both engines. Off: `pipelineCheck`/`prePushCheck`
  run only each module's plain `detekt` task, and nothing is compiled. On: they run detekt's own
  type-resolved `detektMain`/`detektTest`, which on Android cover every variant whose build type
  is not in `detekt.ignoredBuildTypes` (a flavored app analyses each flavor). Previously engine 2
  (and engine 1 with AGP 8 + kotlin-android) also ran every variant task with type resolution off.
- With type resolution on, an Android module whose build types are all in
  `detekt.ignoredBuildTypes` fails with a message instead of silently analysing nothing.
- The classpath override that picked a Kotlin compile task by task-name substring is removed;
  detekt's tasks use their own compilation's classpath.
- Each detekt task analyses only its own sources; previously every detekt task re-analysed the
  whole module. On Android, `src/main` is still analysed once per non-ignored variant, so its
  findings repeat per variant.
- Engine 1 on AGP 9 built-in Kotlin (no detekt variant tasks there): with type resolution on, the
  plain `detekt` task gets one variant's classpath, android.jar included: the first whose build
  type is not in `detekt.ignoredBuildTypes`, preferring `debug`.

### Changed

- Default compose rules bumped to `detekt-rules-compose[-detekt2]` 2.1.1 (`ConditionCouldBeLifted`
  no longer reports an `if` next to `slot?.invoke()` and other statements with composable calls).
- For detekt, `detekt.ignoredBuildTypes` is passed to detekt, which skips those variants' tasks
  (exact, case-sensitive build type match), instead of AQP filtering task names by substring.
  Android lint task filtering is unchanged.
- Kotlin Multiplatform modules keep the plain `detekt` task with type resolution on and log a
  warning: there is no single compilation to analyse.
- Android modules without Kotlin keep the plain `detekt` task with type resolution on.

### Known limitations

- Engine 1 (detekt 1.23.8, a Kotlin 2.0 compiler) cannot read class metadata of project
  dependencies compiled with a Kotlin language version newer than 2.1: their types stay
  unresolved, so type-resolution rules miss findings involving them. Engine 2 has no such limit.

### Upgrading from 3.0.x

1. Engine 2 users who saw type-resolution-only findings (`UnusedPrivateProperty`,
   `UseDataClass`, ...) with `typeResolution` off got them by accident; set
   `detekt.typeResolution.set(true)` to keep them. The same applies to engine 1 with AGP 8 +
   kotlin-android, whose `detektDebug` ran implicitly.
2. Report paths under `build/reports/detekt/`: `detekt.*` with type resolution off. With it on:
   `main.*`, `test.*` on JVM modules; `<variant>.*`, `<variant>UnitTest.*`,
   `<variant>AndroidTest.*` per analysed variant on Android modules (engine 2, or AGP 8 +
   kotlin-android); still `detekt.*` for engine 1 on AGP 9 built-in Kotlin and for Java-only
   Android modules.
3. Expect new findings with type resolution on; refresh baselines. A baseline created by
   `detektBaseline` still applies to the type-resolved tasks.
4. With type resolution on, `detekt.sources.include` and `detekt.sources.exclude` apply only to
   the plain task; detekt's own tasks analyse their compilation's source dirs, so exclude patterns
   such as `src/main/...` no longer filter them. Exclude generated code in the detekt config
   (rule-set `excludes:` patterns) instead.
5. `detekt.ignoredBuildTypes` entries must match build type names exactly (case-sensitive).
6. With type resolution on, a module whose build types are all in `detekt.ignoredBuildTypes` now
   fails the build: remove a build type from the list or turn type resolution off.

## [3.0.0] - 2026-09-28

### Changed

- **Breaking:** the `kode` rule set is resolved from Maven Central instead of a jar bundled in the
  plugin. Default `detekt.android.rules` is `ru.kode:detekt-rules:2.0.0` (engine 1) or
  `ru.kode:detekt-rules-detekt2:2.0.0` (engine 2); default `detekt.compose.rules` is
  `ru.kode:detekt-rules-compose:2.1.0` / `ru.kode:detekt-rules-compose-detekt2:2.1.0`. A matching
  `libs` catalog alias still wins (`detekt-rules` / `detekt-rules-detekt2`, `detekt-compose-rules` /
  `detekt-rules-compose-detekt2`).
- **Breaking:** removed the bundled `kode-android-rules-1.4.0.jar`, the
  `generateDefaultDetektAndroidRulesJar` task and the `rules.defaultFiles` DSL.
- **Breaking:** default Kotlin config: `EnumNaming` now requires PascalCase entries
  (`'[A-Z](?![A-Z]*$)[a-zA-Z0-9]*|[A-Z]'`): multi-letter ALL-CAPS entries such as `GET` or `VK`
  are reported, single letters are still allowed.
- Default Android config: `MissingTypeDeclaration` and `ComponentFunctionCall` are inactive,
  `BlockingSqlDelightCall` targets `app.cash.sqldelight`.
- Default Compose config: dropped `ModifierParameterPosition` and `ComposeFunctionName`, unknown to
  `detekt-rules-compose` 2.x.
- **Breaking:** the minimum AGP is 8.7.3 (was 7.4.0), the oldest version tested; older AGP fails
  with the "UNSUPPORTED ANDROID GRADLE PLUGIN VERSION" message.
- Built with Kotlin 2.4.20, AGP 9.4.1, Gradle 9.8.0.

### Added

- detekt engine switch: Gradle property `ru.kode.appQuality.detektEngine=1|2` (default `1`).
  Engine 2 runs detekt 2 (`dev.detekt` 2.0.0-alpha.6); the consumer puts it on the plugin
  classpath with `id("dev.detekt") version "2.0.0-alpha.6" apply false`. The Kotlin slot then uses
  `dev.detekt:detekt-rules-ktlint-wrapper` (catalog alias `detekt-rules-ktlint-wrapper`) and a
  detekt 2 flavour of the bundled Kotlin config.
- `detekt.buildUponDefaultConfig` (default `false`).
- A warning when a leftover 2.x rules jar (`libs/detekt-rules-1.x.y.jar` or
  `libs/kode-android-rules-1.x.y.jar`) is found (the `kode` rules would run twice).
- Clear failures for an unsupported engine value, engine 2 without `dev.detekt` on the classpath
  or with a `dev.detekt` version other than 2.0.0-alpha.6, engine 2 on a Kotlin Gradle plugin older
  than 2.1.21, and a module applying the other engine's detekt plugin.

### Fixed

- A JVM-only build without AGP on the classpath crashed with
  `ClassNotFoundException: AndroidComponentsExtension`.

### Upgrading from 2.x

1. Delete `libs/detekt-rules-1.4.0.jar` and any `rules { from(files(".../detekt-rules-1.4.0.jar")) }`
   entry; the rules now come from Maven Central, so `mavenCentral()` must be in the project
   repositories. To keep a jar instead, set `detekt.android.rules.useDefaults.set(false)`.
2. Remove `rules.defaultFiles` usages.
3. Expect `EnumNaming` findings on ALL-CAPS enum entries, new findings from the fixed rules in
   `ru.kode:detekt-rules` 2.0.0 and `detekt-rules-compose` 2.1.0 (e.g.
   `ComposableParametersOrdering`), and fewer from the rules now inactive by default; refresh
   baselines.
4. Engine 2 (opt-in):
   - add `id("dev.detekt") version "2.0.0-alpha.6" apply false` to the root `plugins {}` and set
     `ru.kode.appQuality.detektEngine=2`;
   - detekt 2 validates custom configs strictly: it rejects the `build:` and `output-reports:`
     keys, removed rules and renamed properties (`threshold` → `allowed*`); detekt 1 reports at
     `>= threshold`, detekt 2 above `allowed*`, so use `threshold - 1` (except `NamedArguments`
     and `NestedScopeFunctions`, which keep their value); the bundled detekt 2 config does so;
     `config.excludes` must be a list (`[]`, not `''`);
   - drop engine 1 rule artifacts (e.g. `ru.kode:detekt-rules-compose:1.4.0`) from `rules {}`:
     detekt 2 merges every plugin jar's default config and fails with `found duplicate key`;
   - several rules are renamed or moved (`UnusedImports` → `UnusedImport`,
     `UntilInsteadOfRangeTo` → `RangeUntilInsteadOfRangeTo`, `UnusedPrivateMember` →
     `UnusedPrivateFunction`/`UnusedPrivateProperty`); update custom configs and baselines;
   - `UnusedPrivateProperty` and `UseDataClass` only report with `detekt.typeResolution` on;
   - `CyclomaticComplexMethod` counts some constructs higher than detekt 1 with the same
     threshold, so large `when`/branching functions may newly be reported;
   - `dev.detekt` must be exactly 2.0.0-alpha.6, the version the plugin is built against;
   - needs KGP 2.1.21+ (`KotlinJvmExtension`); engine 1 still works with KGP 2.0.21;
   - `detekt.xmlReportEnabled` enables detekt 2's `checkstyle` report, as detekt 2 has no `xml`
     report; it is the same checkstyle XML at the same path (`build/reports/detekt/<task>.xml`),
     so only report tooling that parses detekt-specific attributes needs a look;
   - the detekt 2 task's `basePath` is an absolute input: a checkout at another path misses the
     build cache; with no report enabled the task declares no outputs and is never cached;
   - the custom rule sets must be built against `dev.detekt:detekt-api`.
5. AGP 8.7.3 is the minimum (was 7.4.0).
6. Isolated projects are not supported (the root-applied plugin configures subprojects), as in 2.x.

## [2.0.3] - 2026-08-22

### Fixed

- `detekt.baseline` collided across subprojects when `app-quality-plugin` is applied at the root
  only (no per-module convention plugin): the configured value is a single root-scoped
  `RegularFileProperty`, so every subproject's `detektBaseline*` task wrote to the exact same file
  path, and whichever module's task ran last silently overwrote every other module's baseline
  entries. The baseline is now re-resolved under each subproject's own directory by filename, so
  root-only application produces one baseline file per subproject, matching the behavior
  per-module convention-plugin usage already had.

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
