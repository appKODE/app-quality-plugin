# Migration Guide

Upgrade notes per release. Sections list breaking changes first, then behavior changes and
new opt-in capabilities.

## 3.1.0 (type resolution fix) — migrating from 3.0.x

### Behavior change: `detekt.typeResolution` decides which detekt tasks run

With `typeResolution` off, `pipelineCheck`/`prePushCheck` run only the plain `detekt` task per
module. Engine 2, and engine 1 with AGP 8 + kotlin-android, no longer run every variant task on
top of it, so type-resolution-only findings disappear: set `detekt.typeResolution.set(true)` to
keep them. With it on, detekt's own type-resolved tasks run for one variant (see README "Type
resolution"); expect new findings and report files named after the task (`debug.xml`,
`main.xml`, ...), and refresh baselines. An Android module whose build types are all in
`detekt.ignoredBuildTypes` now fails with type resolution on. Details:
[CHANGELOG.md](CHANGELOG.md) "Upgrading from 3.0.x".

## 3.0.0 (rules from Maven Central, detekt engine switch) — migrating from 2.x

### Breaking: bundled `kode` rules jar removed

`detekt.android.rules` now defaults to `ru.kode:detekt-rules:2.0.0` from Maven Central
(`ru.kode:detekt-rules-detekt2:2.0.0` on engine 2), so `mavenCentral()` must be in the project
repositories. The bundled jar, `generateDefaultDetektAndroidRulesJar` and `rules.defaultFiles` are
gone. Delete `libs/detekt-rules-1.4.0.jar` and any `from(files(...))` entry pointing at it (the
plugin warns when it finds one), or keep it with `detekt.android.rules.useDefaults.set(false)`.
The "bundled default" notes in the 2.0.0 section below no longer apply.

### New: detekt 2 engine

Opt in with `ru.kode.appQuality.detektEngine=2` plus
`id("dev.detekt") version "2.0.0-alpha.6" apply false` in the root build. Custom configs must drop
the `build:` and `output-reports:` keys, use detekt 2 rule and property names and a list for
`config.excludes`; engine 1 rule artifacts must leave `rules {}`. `dev.detekt` must be exactly
2.0.0-alpha.6 and the Kotlin Gradle plugin 2.1.21+ (both checked with a clear failure).
`detekt.xmlReportEnabled` enables detekt 2's `checkstyle` report (detekt 2 has no `xml` report):
the same checkstyle XML at the same `build/reports/detekt/<task>.xml` path. Full list:
[CHANGELOG.md](CHANGELOG.md) "Upgrading from 2.x".

### Breaking: minimum AGP 8.7.3

The minimum Android Gradle Plugin is now 8.7.3 (was 7.4.0), the oldest version tested.

## 2.0.0 (dependency wiring rework) — migrating from 1.0.8

### Breaking: `detekt.<platform>.rulesPluginJar` removed

The single-file `rulesPluginJar: RegularFileProperty` no longer exists. Rule jars are added
through the platform's unified `rules` slot:

```kotlin
// before (1.0.8):
appQualityFoundation {
    detekt.kotlin.rulesPluginJar.set(rootProject.layout.projectDirectory.file("libs/detekt-rules-1.4.0.jar"))
}

// after:
appQualityFoundation {
    detekt.kotlin.rules {
        from(files(rootProject.layout.projectDirectory.file("libs/detekt-rules-1.4.0.jar")))
    }
}
```

### Breaking: implicit `<root>/libs/detekt-rules-1.4.0.jar` default removed

Superseded in 3.0.0 (see "Breaking: bundled `kode` rules jar removed" above): the rules now
come from Maven Central. Previously the plugin silently picked up
`<root>/libs/detekt-rules-1.4.0.jar` when it existed. That implicit, path-based pickup is gone. **However**, see "New: bundled default for
`detekt.android.rules`" below — for the common case (bundled `default.android-config.yml`,
which is the only config that activates `kode:` today) the plugin now supplies an equivalent
default again, just as an explicit, inspectable dependency slot instead of a silent file
convention. Projects with a custom detekt config that activates `kode:` (not the bundled one)
still need the explicit `rules { from(files(...)) }` shown above, or detekt fails config
validation with an unknown `kode` rule set.

### New: bundled default for `detekt.android.rules`

`detekt.android.rules` now has a real default: the plugin bundles its own `kode:` rules jar
(not published to any Maven repo — verified against Maven Central; the only published
`ru.kode` detekt artifact is `detekt-rules-compose`, a different ruleset) and wires it in
automatically while `useDefaults` is `true` (the default). Zero-config projects using the
plugin's bundled `default.android-config.yml` need **no action** — this restores the
1.0.7/1.0.8 zero-config experience for the KODE `RouteWiringMethodNaming` rule, just via an
inspectable slot instead of an implicit file convention. Only projects that explicitly set
`detekt.android.rules { useDefaults.set(false) }` need to supply their own jar/coordinate.

### New: `ktlint.cli`/`detekt.kotlin.rules`/`detekt.compose.rules` no longer require a catalog alias

These 3 slots now fall back to a coordinate baked into the plugin when the consumer's `libs`
catalog has no matching alias (or no catalog at all) — previously this was a hard failure
("MISSING KTLINT/DETEKT DEPENDENCY IN VERSION CATALOG" / "MISSING VERSION CATALOG"). A
matching alias in your own catalog, if present, still wins unchanged — **no action required**
for existing projects with the standard `ktlint-cli`/`detekt-formatting`/`detekt-compose-rules`
aliases already declared.

### New: unified dependency slots (`from(...)` from any source + `useDefaults`)

Every external dependency of the plugin — the ktlint CLI and each detekt platform's rule
sets — is now ONE uniform slot accepting every source kind:

```kotlin
appQualityFoundation {
    ktlint.cli {
        from(deps.ktlint.cli)                             // typed accessor from ANY catalog
        from("com.pinterest.ktlint:ktlint-cli:1.8.0")     // string coordinates
        from(files("tools/ktlint-cli.jar"))               // checked-in jar files
        useDefaults.set(false)                            // drop the `libs` catalog default
    }
    detekt.kotlin.rules { from(files("libs/detekt-rules-1.4.0.jar")) }
    detekt.compose.rules {
        from("ru.kode:detekt-rules-compose:1.4.0")        // published custom rules
        useDefaults.set(false)
    }
}
```

Semantics:
- `from(...)` is add-only; entries from all sources accumulate.
- The slot's default (the `libs` catalog aliases `ktlint-cli`, `detekt-formatting`,
  `detekt-compose-rules`) is independent of user additions and included while
  `useDefaults` is `true` (the default) — so adding your custom rules jar keeps the
  default formatting rules unless you disable them.
- Zero-config projects with the standard `libs` aliases need NO changes beyond the rules-jar
  migration above.

### Behavior change: configured-but-missing files fail the build

A file listed in any slot (`from(files(...))`) that does not exist on disk fails the build
with an explanatory message naming the slot. Note the validation fires whenever the
dependency set is realized — including IDE sync and the `dependencies` report — not only on
task execution.

### Breaking: `additionalSourcePatterns`/`additionalIgnoredSourcePatterns` (ktlint) and
`additionalSourcePaths`/`additionallyExcludedPaths` (detekt) replaced by `sources { }`

Both blocks' raw `ListProperty<String>` source-pattern properties are replaced by a single
`sources { }` block, mirroring the dependency slots' `include`/`exclude`/`useDefaults` shape.
The leaky `!` prefix ktlint ignores required is gone — `exclude` now takes bare patterns; the
plugin adds the CLI's `!` prefix internally.

```kotlin
// before (1.0.8):
appQualityFoundation {
    ktlint {
        additionalSourcePatterns.set(listOf("**/src/*/kotlin/**/*.kts"))
        additionalIgnoredSourcePatterns.set(listOf("!**/build-logic/**"))
    }
    detekt {
        additionalSourcePaths.set(listOf("src/custom/kotlin"))
        additionallyExcludedPaths.set(listOf("tmpGenerated"))
    }
}

// after:
appQualityFoundation {
    ktlint.sources {
        include.set(listOf("**/src/*/kotlin/**/*.kts"))
        exclude.set(listOf("**/build-logic/**"))   // no `!` prefix
    }
    detekt.sources {
        include.set(listOf("src/custom/kotlin"))
        exclude.set(listOf("tmpGenerated"))
    }
}
```

`useDefaults.set(false)` on either block drops the plugin's bundled defaults (ktlint's
default Kotlin globs/ignore list, detekt's default per-platform source dirs) — same
`useDefaults` semantics as the dependency slots.

### New: `detekt.baseline`, `detekt.xmlReportEnabled`, `detekt.sarifReportEnabled`

Three new opt-in detekt configuration properties for incremental adoption and report format control:

- `baseline`: optional detekt baseline file (e.g. `detekt-baseline.xml`). Findings present in the
  baseline are suppressed. Unset by default (no baseline).
- `xmlReportEnabled`: emit detekt's XML report per task. Default `false`.
- `sarifReportEnabled`: emit detekt's SARIF report per task (e.g. for GitHub code scanning). Default `false`.

Example:

```kotlin
appQualityFoundation {
    detekt {
        baseline.set(layout.projectDirectory.file("detekt-baseline.xml"))
        xmlReportEnabled.set(true)
        sarifReportEnabled.set(false)
    }
}
```

### New: opt-in `androidLint.enabled` wiring

Integrate Android Gradle Plugin lint checks into `pipelineCheck` and `prePushCheck` aggregate tasks via
the new `androidLint { enabled.set(true) }` config. Off by default — lint is slow and most projects
already run it separately in CI.

Example:

```kotlin
appQualityFoundation {
    androidLint {
        enabled.set(true)
    }
}
```

### Upgrade checklist for KODE projects

1. Replace every `detekt.<platform>.rulesPluginJar.set(...)` with
   `detekt.<platform>.rules { from(files(...)) }`.
2. If your project relied on the implicit `libs/detekt-rules-1.4.0.jar` pickup with a
   **custom** detekt config (not the plugin's bundled `default.android-config.yml`), add the
   same `rules { from(files(...)) }` line. Projects using the bundled android config need no
   action — see "New: bundled default for `detekt.android.rules`" above.
3. The `libs` catalog aliases (`ktlint-cli`, `detekt-formatting`, `detekt-compose-rules`) are
   now optional — only needed if you want a version different from the plugin's own baked-in
   default, or to disable a default entirely with `useDefaults.set(false)`.
4. Replace `ktlint.additionalSourcePatterns`/`additionalIgnoredSourcePatterns` and
   `detekt.additionalSourcePaths`/`additionallyExcludedPaths` with `ktlint.sources { }` /
   `detekt.sources { }` as shown above — drop the `!` prefix from any exclude pattern.
5. Run `./gradlew pipelineCheck` and check CI is green.
6. If your project only applies the plugin at the root and configures `verboseLogging` (the
   shape used by every current adopter) — no action needed. That zero-config shape is now
   covered by an explicit test (`RealProjectShapeTest`) and needs no changes to keep working.
