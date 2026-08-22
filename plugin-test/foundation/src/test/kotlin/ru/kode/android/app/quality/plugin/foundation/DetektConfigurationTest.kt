package ru.kode.android.app.quality.plugin.foundation

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.kode.android.app.quality.plugin.foundation.messages.missingKodeRuleSetDependencyMessage
import ru.kode.android.app.quality.plugin.test.utils.DependencySlot
import ru.kode.android.app.quality.plugin.test.utils.DetektBlock
import ru.kode.android.app.quality.plugin.test.utils.LibsCatalog
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.PlatformDetektBlock
import ru.kode.android.app.quality.plugin.test.utils.QualityConfig
import ru.kode.android.app.quality.plugin.test.utils.SourcePatternsSlot
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.runTask
import ru.kode.android.app.quality.plugin.test.utils.runTaskWithFail
import ru.kode.android.app.quality.plugin.test.utils.runTasks
import java.io.File

class DetektConfigurationTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        projectDir = File(tempDir, "test-project")
    }

    @Test
    fun `module-local detekt-kotlin-config yml is used for that module`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_MAX_LINE_60,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Long.kt" to Sources.LONG_LINE_80),
                    ),
                ),
        )

        val result = projectDir.runTaskWithFail(":a:detekt")

        assertEquals(TaskOutcome.FAILED, result.task(":a:detekt")?.outcome)
        assertTrue(result.output.contains("MaxLineLength"), "expected MaxLineLength violation from module config")
    }

    @Test
    fun `bundled default kotlin config is used when module has no config file`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Long.kt" to Sources.LONG_LINE_80),
                    ),
                ),
        )

        // Bundled default allows up to 120 chars — the 80-char line must pass.
        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    // Regression guard: per-module config resolution goes through the shared extension object today;
    // behaviorally each module gets its own config (verified) — this must stay true after the rework.
    @Test
    fun `module without config is not contaminated by sibling module's config`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_MAX_LINE_60,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Long.kt" to Sources.LONG_LINE_80),
                    ),
                    ModuleSpec(
                        name = "b",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Long.kt" to Sources.LONG_LINE_80),
                    ),
                ),
        )

        // b has no module config -> bundled default (120) -> must pass even though a's config (60) exists.
        // (A behavioral check: if a's 60-char limit leaked into b, this task would fail.)
        val result = projectDir.runTask(":b:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":b:detekt")?.outcome)
    }

    @Test
    fun `extension-level detekt kotlin projectConfig override applies to all modules`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Long.kt" to Sources.LONG_LINE_80),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt =
                        DetektBlock(
                            kotlin = PlatformDetektBlock(projectConfigPath = "config/strict-detekt.yml"),
                        ),
                ),
            extraRootFiles = mapOf("config/strict-detekt.yml" to Configs.DETEKT_MAX_LINE_60),
        )

        val result = projectDir.runTaskWithFail(":a:detekt")

        assertEquals(TaskOutcome.FAILED, result.task(":a:detekt")?.outcome)
        assertTrue(result.output.contains("MaxLineLength"), "expected violation from the override config")
    }

    @Test
    fun `no rules jar configured does not break the build`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            // rulesPluginJars has no default: nothing configured -> nothing added, build is fine
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `configured rules jar makes the kode rule set available to detekt`() {
        val rulesJar = exampleRulesJar()
        assumeTrue(rulesJar != null, "example-project rules jar not found; skipping")

        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_KODE_RULE,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig = QualityConfig(detekt = kodeRulesJarBlock()),
            rulesJar = rulesJar,
        )

        // Config validation accepts the `kode` rule set only when the jar is on detektPlugins.
        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `kode rule set config without the rules jar fails with the actionable plugin message`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_KODE_RULE,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            // no rulesJar -> the `kode` rule set is unknown to detekt
        )

        val result = projectDir.runTaskWithFail(":a:detekt")

        assertTrue(
            result.output.contains("MISSING DEPENDENCY FOR 'kode' RULE SET"),
            "expected the plugin's actionable message, not detekt's raw error",
        )
        assertTrue(result.output.contains("detekt.kotlin.rules"), "expected the specific slot to be named")
        assertFalse(
            result.output.contains("Property 'kode' is misspelled or does not exist"),
            "the plugin's message should preempt detekt's raw config-validation error",
        )
        val configFile = File(projectDir, "a/detekt-kotlin-config.yml").canonicalFile
        val expectedMessage = missingKodeRuleSetDependencyMessage("kotlin", configFile)
        assertTrue(
            result.output.contains(expectedMessage),
            "expected the verbatim missingKodeRuleSetDependencyMessage banner in the output",
        )
    }

    @Test
    fun `android module using the bundled default config succeeds via the bundled kode rules jar default`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.AndroidLib,
                        // no detektAndroidConfigContent override -> uses the bundled
                        // default.android-config.yml, which activates `kode:` via
                        // RouteWiringMethodNaming. Zero-config: detekt.android.rules now has
                        // a bundled default (the plugin's own kode rules jar resource), so
                        // this must succeed with no qualityConfig at all.
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `android rules useDefaults false without a replacement fails with the actionable plugin message`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.AndroidLib,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt =
                        DetektBlock(
                            android = PlatformDetektBlock(rules = DependencySlot(useDefaults = false)),
                        ),
                ),
        )

        // Bundled android config still activates `kode:`, but the default that would satisfy
        // it was explicitly disabled and nothing else was wired — a deliberate opt-out.
        val result = projectDir.runTaskWithFail(":a:detekt")

        assertTrue(
            result.output.contains("MISSING DEPENDENCY FOR 'kode' RULE SET"),
            "expected the plugin's actionable message when the android default is disabled",
        )
        assertTrue(result.output.contains("detekt.android.rules"), "expected the android slot to be named")
    }

    @Test
    fun `android rules useDefaults false with an explicit jar still succeeds`() {
        val rulesJar = exampleRulesJar()
        assumeTrue(rulesJar != null, "example-project rules jar not found; skipping")

        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.AndroidLib,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt =
                        DetektBlock(
                            android =
                                PlatformDetektBlock(
                                    rules =
                                        DependencySlot(
                                            files = listOf("libs/detekt-rules-1.4.0.jar"),
                                            useDefaults = false,
                                        ),
                                ),
                        ),
                ),
            rulesJar = rulesJar,
        )

        // The bundled default is off, but the user-supplied jar fully replaces it.
        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `kotlin module using the bundled default config never triggers the kode rule set check`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        // no detektKotlinConfigContent override -> uses the bundled
                        // default.kotlin-config.yml, whose only "kode" substring is an
                        // unrelated forbidden-import value (ru.kode.remo.ReactiveModel) —
                        // must not false-positive against the anchored `^kode:` check.
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
        assertFalse(result.output.contains("MISSING DEPENDENCY FOR 'kode' RULE SET"))
    }

    @Test
    fun `detekt sources include restricts check to only the matching sources`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_MAX_LINE_60,
                        kotlinSources =
                            mapOf(
                                "src/main/kotlin/ru/kode/test/Long.kt" to Sources.LONG_LINE_80,
                                "src/main/kotlin/ru/kode/legacy/Long.kt" to Sources.LONG_LINE_80,
                            ),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt = DetektBlock(sources = SourcePatternsSlot(include = listOf("**/test/**"))),
                ),
        )

        // Only ru/kode/test/Long.kt matches the include pattern — the violating
        // ru/kode/legacy/Long.kt is never analyzed, so only the matched file's violation fires.
        val result = projectDir.runTaskWithFail(":a:detekt")

        assertEquals(TaskOutcome.FAILED, result.task(":a:detekt")?.outcome)
        assertTrue(result.output.contains("Long.kt"), "expected a violation from the included source")
    }

    @Test
    fun `detekt sources exclude excludes matching sources from check`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_MAX_LINE_60,
                        kotlinSources =
                            mapOf(
                                "src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE,
                                "src/main/kotlin/ru/kode/legacy/Long.kt" to Sources.LONG_LINE_80,
                            ),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt = DetektBlock(sources = SourcePatternsSlot(exclude = listOf("**/legacy/**"))),
                ),
        )

        // The only violating source is excluded from analysis entirely.
        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `detekt sources useDefaults false drops the bundled Kotlin globs`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_MAX_LINE_60,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Long.kt" to Sources.LONG_LINE_80),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt = DetektBlock(sources = SourcePatternsSlot(useDefaults = false)),
                ),
        )

        // useDefaults=false with no custom include leaves nothing matched — the task has no
        // source at all, so it is skipped rather than run against the violating source.
        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.NO_SOURCE, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `missing detekt-formatting alias in version catalog still succeeds via the baked-in default`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            libsCatalog = LibsCatalog(includeDetektFormatting = false),
        )

        // No `detekt-formatting` alias anywhere in the catalog — the plugin falls back to its
        // own baked-in coordinate instead of failing.
        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `enabling typeResolution invalidates the detekt task's up-to-date state`() {
        val module =
            ModuleSpec(
                name = "a",
                type = ModuleType.KotlinJvm,
                kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
            )
        projectDir.createQualityProject(modules = listOf(module))
        val first = projectDir.runTask(":a:detekt")
        assertEquals(TaskOutcome.SUCCESS, first.task(":a:detekt")?.outcome)

        projectDir.createQualityProject(
            modules = listOf(module),
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = true)),
        )
        val second = projectDir.runTask(":a:detekt")

        assertTrue(
            second.task(":a:detekt")?.outcome != TaskOutcome.UP_TO_DATE,
            "enabling typeResolution must wire the compile task's classpath onto detekt and invalidate " +
                "its cached result, got ${second.task(":a:detekt")?.outcome}",
        )
    }

    @Test
    fun `typeResolution false misses a violation that requires resolved types`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_UNNECESSARY_SAFE_CALL,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.UNNECESSARY_SAFE_CALL),
                    ),
                ),
            // typeResolution defaults to false: detekt has no classpath and cannot resolve
            // that `value` is a non-null String, so UnnecessarySafeCall cannot fire.
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `typeResolution true catches a violation that requires resolved types`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_UNNECESSARY_SAFE_CALL,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.UNNECESSARY_SAFE_CALL),
                    ),
                ),
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = true)),
        )

        val result = projectDir.runTaskWithFail(":a:detekt")

        assertEquals(TaskOutcome.FAILED, result.task(":a:detekt")?.outcome)
        assertTrue(
            result.output.contains("UnnecessarySafeCall"),
            "expected UnnecessarySafeCall to fire once type resolution is enabled, got: ${result.output}",
        )
    }

    @Test
    fun `compose rules useDefaults false without a replacement fails config validation`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        applyComposePlugin = true,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt =
                        DetektBlock(
                            compose = PlatformDetektBlock(rules = DependencySlot(useDefaults = false)),
                        ),
                ),
        )

        // Bundled compose config activates `compose:`, but the default rules-compose jar that
        // would satisfy it was explicitly disabled and nothing else was wired.
        val result = projectDir.runTaskWithFail(":a:detekt")

        assertEquals(TaskOutcome.FAILED, result.task(":a:detekt")?.outcome)
        assertTrue(result.output.contains("compose"), "expected a validation error naming the compose rule set")
    }

    @Test
    fun `compose rules useDefaults false with an explicit coordinate still succeeds`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        applyComposePlugin = true,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt =
                        DetektBlock(
                            compose =
                                PlatformDetektBlock(
                                    rules =
                                        DependencySlot(
                                            notations = listOf("ru.kode:detekt-rules-compose:1.4.0"),
                                            useDefaults = false,
                                        ),
                                ),
                        ),
                ),
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `detekt sources exclude still applies when sources useDefaults is false`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_MAX_LINE_60,
                        kotlinSources =
                            mapOf(
                                "src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE,
                                "src/main/kotlin/ru/kode/test/legacy/Long.kt" to Sources.LONG_LINE_80,
                            ),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt =
                        DetektBlock(
                            sources =
                                SourcePatternsSlot(
                                    useDefaults = false,
                                    include = listOf("**/test/**"),
                                    exclude = listOf("**/legacy/**"),
                                ),
                        ),
                ),
        )

        // legacy/Long.kt matches the custom include ("**/test/**") but excludes are unconditional
        // (applied regardless of useDefaults) — it must still be filtered out.
        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `kode jar wired only via kotlin rules still satisfies the android platform of the same module`() {
        val rulesJar = exampleRulesJar()
        assumeTrue(rulesJar != null, "example-project rules jar not found; skipping")

        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.AndroidLib,
                        // no override -> bundled default.android-config.yml activates `kode:`
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt =
                        DetektBlock(
                            // android's own bundled kode default is disabled...
                            android = PlatformDetektBlock(rules = DependencySlot(useDefaults = false)),
                            // ...but the kotlin platform (also configured for an AndroidLib
                            // module) wires the same jar into the shared detektPlugins config.
                            kotlin =
                                PlatformDetektBlock(
                                    rules = DependencySlot(files = listOf("libs/detekt-rules-1.4.0.jar")),
                                ),
                        ),
                ),
            rulesJar = rulesJar,
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `verboseLogging defaults to false and suppresses detekt debug output`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
        assertFalse(
            result.output.contains("Phase LoadConfig took"),
            "verboseLogging defaults to false; detekt's debug phase timings must not appear",
        )
    }

    @Test
    fun `verboseLogging true enables detekt debug output`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig = QualityConfig(verboseLogging = true),
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
        assertTrue(
            result.output.contains("Phase LoadConfig took"),
            "verboseLogging=true must enable detekt's debug phase timings",
        )
    }

    @Test
    fun `changing jvmTarget invalidates the detekt task's up-to-date state`() {
        val module =
            ModuleSpec(
                name = "a",
                type = ModuleType.KotlinJvm,
                kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
            )
        projectDir.createQualityProject(
            modules = listOf(module),
            qualityConfig = QualityConfig(jvmTarget = "JVM_17"),
        )
        val first = projectDir.runTask(":a:detekt")
        assertEquals(TaskOutcome.SUCCESS, first.task(":a:detekt")?.outcome)

        projectDir.createQualityProject(
            modules = listOf(module),
            qualityConfig = QualityConfig(jvmTarget = "JVM_11"),
        )
        val second = projectDir.runTask(":a:detekt")

        assertTrue(
            second.task(":a:detekt")?.outcome != TaskOutcome.UP_TO_DATE,
            "changing jvmTarget must invalidate detekt's cached result, got ${second.task(":a:detekt")?.outcome}",
        )
    }

    @Test
    fun `xml and sarif reports are off by default`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
        assertTrue(
            !File(projectDir, "a/build/reports/detekt/detekt.xml").exists(),
            "expected no XML report when xmlReportEnabled is left at its default",
        )
        assertTrue(
            !File(projectDir, "a/build/reports/detekt/detekt.sarif").exists(),
            "expected no SARIF report when sarifReportEnabled is left at its default",
        )
    }

    @Test
    fun `xmlReportEnabled writes the detekt XML report`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig = QualityConfig(extraExtensionContent = "detekt.xmlReportEnabled.set(true)"),
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
        assertTrue(
            File(projectDir, "a/build/reports/detekt/detekt.xml").exists(),
            "expected an XML report once xmlReportEnabled is set",
        )
    }

    @Test
    fun `sarifReportEnabled writes the detekt SARIF report`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig = QualityConfig(extraExtensionContent = "detekt.sarifReportEnabled.set(true)"),
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
        assertTrue(
            File(projectDir, "a/build/reports/detekt/detekt.sarif").exists(),
            "expected a SARIF report once sarifReportEnabled is set",
        )
    }

    @Test
    fun `baseline suppresses a pre-existing finding so detekt succeeds`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_MAX_LINE_60,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Long.kt" to Sources.LONG_LINE_80),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    extraExtensionContent =
                        "detekt.baseline.set(rootProject.layout.projectDirectory.file(\"detekt-baseline.xml\"))",
                ),
        )

        // Without a baseline, this violation fails the build (confirms the fixture is valid).
        val withoutBaseline = projectDir.runTaskWithFail(":a:detekt")
        assertEquals(TaskOutcome.FAILED, withoutBaseline.task(":a:detekt")?.outcome)

        val baselineResult = projectDir.runTask(":a:detektBaseline")
        assertEquals(TaskOutcome.SUCCESS, baselineResult.task(":a:detektBaseline")?.outcome)
        assertTrue(
            File(projectDir, "a/detekt-baseline.xml").exists(),
            "expected detektBaseline to write the baseline under the module's own directory, " +
                "even though it was configured once at the root",
        )

        val withBaseline = projectDir.runTask(":a:detekt")
        assertEquals(TaskOutcome.SUCCESS, withBaseline.task(":a:detekt")?.outcome)
    }

    @Test
    fun `root-only baseline config resolves to a separate file per subproject`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_MAX_LINE_60,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Long.kt" to Sources.LONG_LINE_80),
                    ),
                    ModuleSpec(
                        name = "b",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_MAX_LINE_60,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Long.kt" to Sources.LONG_LINE_80),
                    ),
                ),
            // Mirrors root-only application (no per-module convention plugin): configured ONCE,
            // on the shared root-scoped extension, exactly like dreamisland's setup.
            qualityConfig =
                QualityConfig(
                    extraExtensionContent =
                        "detekt.baseline.set(rootProject.layout.projectDirectory.file(\"detekt-baseline.xml\"))",
                ),
        )

        projectDir.runTasks(":a:detektBaseline", ":b:detektBaseline")

        val baselineA = File(projectDir, "a/detekt-baseline.xml")
        val baselineB = File(projectDir, "b/detekt-baseline.xml")
        assertTrue(baselineA.exists(), "expected module a to get its own baseline file")
        assertTrue(baselineB.exists(), "expected module b to get its own baseline file")
        assertTrue(baselineA.readText().contains("MaxLineLength"), "expected module a's own finding in its baseline")
        assertTrue(baselineB.readText().contains("MaxLineLength"), "expected module b's own finding in its baseline")

        val checkResult = projectDir.runTasks(":a:detekt", ":b:detekt")
        assertEquals(TaskOutcome.SUCCESS, checkResult.task(":a:detekt")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, checkResult.task(":b:detekt")?.outcome)
    }

    @Test
    fun `detekt is not applied to a plain java module`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "javaonly",
                        type = ModuleType.JavaOnly,
                        javaSources =
                            mapOf(
                                "src/main/java/ru/kode/test/Main.java" to
                                    "package ru.kode.test;\n\npublic class Main {}\n",
                            ),
                    ),
                ),
        )

        val result = projectDir.runTasks(":javaonly:tasks", arguments = listOf("--all"))

        assertFalse(
            result.output.contains("detekt "),
            "plain java module must not get detekt tasks",
        )
    }
}
