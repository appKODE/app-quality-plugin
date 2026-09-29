package ru.kode.android.app.quality.plugin.foundation

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import ru.kode.android.app.quality.plugin.test.utils.DependencySlot
import ru.kode.android.app.quality.plugin.test.utils.DetektBlock
import ru.kode.android.app.quality.plugin.test.utils.LibsCatalog
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.PlatformDetektBlock
import ru.kode.android.app.quality.plugin.test.utils.QualityConfig
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.initGit
import ru.kode.android.app.quality.plugin.test.utils.runTasks
import java.io.File

/**
 * The PR-tier behaviour of both detekt engines (`ru.kode.appQuality.detektEngine` = 1 or 2) on
 * the default Gradle/AGP/Kotlin versions. Every test runs once per engine.
 */
class DetektEngineTest {
    @TempDir
    lateinit var tempDir: File

    private fun projectDir(
        engine: Int,
        name: String = "p",
    ) = File(tempDir, "$name$engine")

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `bundled default configs report exactly the expected rule ids`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.AndroidLib,
                        kotlinSources = fixtureSources("kode-violations"),
                    ),
                    ModuleSpec(
                        name = "c",
                        type = ModuleType.KotlinJvm,
                        applyComposePlugin = true,
                        kotlinSources =
                            fixtureSources("compose-violations") +
                                (
                                    "src/main/kotlin/sample/Bad.kt" to
                                        "package sample\n\nfun bad() {\n    println()\n}\n"
                                ),
                    ),
                ),
            qualityConfig = QualityConfig(extraExtensionContent = "detekt.xmlReportEnabled.set(true)"),
            detektEngine = engine,
        )

        projectDir.runTasks(":a:detekt", ":c:detekt", "--continue", expectFailure = true)

        assertEquals(ANDROID_DEFAULT_RULE_IDS.getValue(engine), projectDir.reportedRuleIds("a", "detekt"))
        assertEquals(COMPOSE_DEFAULT_RULE_IDS, projectDir.reportedRuleIds("c", "detekt"))
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `type resolution rules from coordinates fire only with type information`(engine: Int) {
        fun create(
            projectDir: File,
            typeResolution: Boolean,
        ) = projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "t",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = TYPE_RESOLUTION_CONFIG,
                        kotlinSources = fixtureSources("kode-violations"),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt =
                        DetektBlock(
                            typeResolution = typeResolution,
                            kotlin =
                                PlatformDetektBlock(
                                    rules =
                                        DependencySlot(
                                            notations = listOf("ru.kode:${kodeRulesArtifact(engine)}:2.0.0"),
                                        ),
                                ),
                        ),
                    extraExtensionContent = "detekt.xmlReportEnabled.set(true)",
                ),
            detektEngine = engine,
        )

        val withoutTypes = projectDir(engine, "plain")
        create(withoutTypes, typeResolution = false)
        val silent = withoutTypes.runTasks(":t:detekt")
        assertEquals(TaskOutcome.SUCCESS, silent.task(":t:detekt")?.outcome)
        assertEquals(emptySet<String>(), withoutTypes.reportedRuleIds("t", "detekt"))

        val withTypes = projectDir(engine, "typed")
        create(withTypes, typeResolution = true)
        withTypes.runTasks(":t:detektMain", expectFailure = true)
        assertEquals(
            setOf("BlockingSqlDelightCall", "MissingTypeDeclaration"),
            withTypes.reportedRuleIds("t", "main"),
        )
    }

    @ParameterizedTest(name = "engine {0}, buildUponDefaultConfig {1}")
    @CsvSource("1, true", "1, false", "2, true", "2, false")
    fun `buildUponDefaultConfig controls whether detekt defaults apply under a module config`(
        engine: Int,
        buildUpon: Boolean,
    ) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = UNRELATED_RULE_CONFIG,
                        kotlinSources = mapOf("src/main/kotlin/sample/Empty.kt" to EMPTY_FUNCTION_SOURCE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt = DetektBlock(buildUponDefaultConfig = buildUpon),
                    extraExtensionContent = "detekt.xmlReportEnabled.set(true)",
                ),
            detektEngine = engine,
        )

        projectDir.runTasks(":a:detekt", expectFailure = buildUpon)

        assertEquals(
            if (buildUpon) setOf("EmptyFunctionBlock") else emptySet(),
            projectDir.reportedRuleIds("a", "detekt"),
        )
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `baseline is created, suppresses known findings and a new finding still fails`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = EMPTY_BLOCKS_CONFIG,
                        kotlinSources = mapOf("src/main/kotlin/sample/Empty.kt" to EMPTY_FUNCTION_SOURCE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    extraExtensionContent =
                        "detekt.baseline.set(rootProject.layout.projectDirectory.file(\"detekt-baseline.xml\"))",
                ),
            detektEngine = engine,
        )

        projectDir.runTasks(":a:detekt", expectFailure = true)
        projectDir.runTasks(":a:detektBaseline")
        val baseline = File(projectDir, "a/detekt-baseline.xml")
        assertTrue(baseline.readText().contains("EmptyFunctionBlock"), "baseline must record the finding")

        val applied = projectDir.runTasks(":a:detekt")
        assertEquals(TaskOutcome.SUCCESS, applied.task(":a:detekt")?.outcome)

        File(projectDir, "a/src/main/kotlin/sample/Other.kt").writeText("package sample\n\nfun other() {}\n")
        val stale = projectDir.runTasks(":a:detekt", expectFailure = true)
        assertTrue(stale.output.contains("EmptyFunctionBlock"), "a finding outside the baseline must fail")
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `custom rules from a project dependency run`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "rules",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = customRulesSources(engine),
                        extraBuildContent = customRulesDependencies(engine),
                    ),
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = NO_FOO_CONFIG,
                        kotlinSources = mapOf("src/main/kotlin/sample/Foo.kt" to FOO_SOURCE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    extraExtensionContent =
                        """
                        detekt.xmlReportEnabled.set(true)
                        detekt.kotlin.rules { from(project.dependencies.project(path: ':rules')) }
                        """.trimIndent(),
                ),
            detektEngine = engine,
        )

        projectDir.runTasks(":a:detekt", expectFailure = true)

        assertEquals(setOf("NoFoo"), projectDir.reportedRuleIds("a", "detekt"))
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `custom rules from an included build resolve by coordinates`(engine: Int) {
        val projectDir = projectDir(engine)
        val rulesBuildFiles =
            customRulesSources(engine).mapKeys { (path, _) -> "rules-build/$path" } +
                mapOf(
                    "rules-build/settings.gradle" to
                        """
                        dependencyResolutionManagement {
                            repositories { mavenCentral() }
                        }
                        rootProject.name = 'custom-rules'
                        """.trimIndent(),
                    "rules-build/build.gradle" to
                        """
                        plugins { id 'org.jetbrains.kotlin.jvm' }
                        group = 'ru.kode.test'
                        version = '1.0'
                        """.trimIndent() + "\n" + customRulesDependencies(engine),
                )
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = NO_FOO_CONFIG,
                        kotlinSources = mapOf("src/main/kotlin/sample/Foo.kt" to FOO_SOURCE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    extraExtensionContent =
                        """
                        detekt.xmlReportEnabled.set(true)
                        detekt.kotlin.rules { from("ru.kode.test:custom-rules:1.0") }
                        """.trimIndent(),
                ),
            extraRootFiles = rulesBuildFiles,
            detektEngine = engine,
        )
        File(projectDir, "settings.gradle").appendText("\nincludeBuild('rules-build')\n")

        projectDir.runTasks(":a:detekt", expectFailure = true)

        assertEquals(setOf("NoFoo"), projectDir.reportedRuleIds("a", "detekt"))
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `disabled android rules slot fails with the actionable kode message`(engine: Int) {
        val projectDir = projectDir(engine)
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
                    detekt = DetektBlock(android = PlatformDetektBlock(rules = DependencySlot(useDefaults = false))),
                ),
            detektEngine = engine,
        )

        val result = projectDir.runTasks(":a:detekt", expectFailure = true)

        assertTrue(result.output.contains("MISSING DEPENDENCY FOR 'kode' RULE SET"), "expected the kode message")
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `custom config path is used and a missing one fails clearly`(engine: Int) {
        fun create(
            projectDir: File,
            configPath: String,
        ) = projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/sample/Empty.kt" to EMPTY_FUNCTION_SOURCE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt = DetektBlock(kotlin = PlatformDetektBlock(projectConfigPath = configPath)),
                    extraExtensionContent = "detekt.xmlReportEnabled.set(true)",
                ),
            extraRootFiles = mapOf("config/custom.yml" to EMPTY_BLOCKS_CONFIG),
            detektEngine = engine,
        )

        val custom = projectDir(engine, "custom")
        create(custom, "config/custom.yml")
        custom.runTasks(":a:detekt", expectFailure = true)
        assertEquals(setOf("EmptyFunctionBlock"), custom.reportedRuleIds("a", "detekt"))

        val missing = projectDir(engine, "missing")
        create(missing, "config/nope.yml")
        val result = missing.runTasks(":a:detekt", expectFailure = true)
        assertTrue(result.output.contains("MISSING DETEKT CONFIG FILE"), "expected the missing config message")
        assertTrue(result.output.contains("detekt.kotlin.projectConfig"), "expected the slot name")
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `a rules version pinned in the version catalog wins over the plugin default`(engine: Int) {
        val projectDir = projectDir(engine)
        val alias = if (engine == 1) "detekt-compose-rules" else "detekt-rules-compose-detekt2"
        projectDir.createQualityProject(
            modules = listOf(ModuleSpec(name = "c", type = ModuleType.KotlinJvm, applyComposePlugin = true)),
            libsCatalog = LibsCatalog(generate = false),
            extraRootFiles =
                mapOf(
                    "gradle/libs.versions.toml" to
                        "[libraries]\n$alias = { module = \"ru.kode:${kodeComposeRulesArtifact(
                            engine,
                        )}\", version = \"2.0.0\" }\n",
                ),
            detektEngine = engine,
        )

        val result = projectDir.runTasks(":c:dependencies", "--configuration", "detektPlugins")

        assertTrue(
            result.output.contains("ru.kode:${kodeComposeRulesArtifact(engine)}:2.0.0"),
            "expected the pinned compose rules version",
        )
        assertFalse(result.output.contains("${kodeComposeRulesArtifact(engine)}:2.1.0"), "default must not win")
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `kotlin DSL consumer reuses the configuration cache`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            useKotlinDsl = true,
            detektEngine = engine,
        )

        val first = projectDir.runTasks(":a:detekt", arguments = listOf("--configuration-cache"))
        assertEquals(TaskOutcome.SUCCESS, first.task(":a:detekt")?.outcome)

        val second = projectDir.runTasks(":a:detekt", arguments = listOf("--configuration-cache"))
        assertTrue(second.output.contains("Reusing configuration cache"), "second run must reuse the cache")
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":a:detekt")?.outcome)
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `detekt output is loaded from the build cache in a relocated copy`(engine: Int) {
        val original = projectDir(engine, "original")
        val cacheDir = File(tempDir, "build-cache$engine")
        original.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            // A report gives the task an output to cache: with none, engine 2 has nothing to restore.
            qualityConfig = QualityConfig(extraExtensionContent = "detekt.xmlReportEnabled.set(true)"),
            detektEngine = engine,
        )
        File(original, "settings.gradle").appendText(
            "\nbuildCache { local { directory = new File('${cacheDir.invariantSeparatorsPath}') } }\n",
        )

        val first = original.runTasks(":a:detekt", arguments = listOf("--build-cache"))
        assertEquals(TaskOutcome.SUCCESS, first.task(":a:detekt")?.outcome)

        val relocated = projectDir(engine, "relocated")
        original.copyRecursively(relocated)
        File(relocated, "build").deleteRecursively()
        File(relocated, "a/build").deleteRecursively()
        File(relocated, ".gradle").deleteRecursively()

        val fromCache = relocated.runTasks(":a:detekt", arguments = listOf("--build-cache"))
        // Known engine difference: detekt 2.0.0-alpha.6 makes the root project's absolute path
        // (`basePath`, used to relativize report paths) a task input, so a relocated checkout
        // misses the cache. Same-path caches (CI on a fixed workspace) still hit, checked below.
        val expected = if (engine == 1) TaskOutcome.FROM_CACHE else TaskOutcome.SUCCESS
        assertEquals(expected, fromCache.task(":a:detekt")?.outcome)

        val rerun = relocated.runTasks(":a:detekt", arguments = listOf("--build-cache"))
        assertEquals(TaskOutcome.UP_TO_DATE, rerun.task(":a:detekt")?.outcome)

        File(original, "a/build").deleteRecursively()
        val samePath = original.runTasks(":a:detekt", arguments = listOf("--build-cache"))
        assertEquals(TaskOutcome.FROM_CACHE, samePath.task(":a:detekt")?.outcome)
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `aggregate tasks and git hooks apply only the selected engine's detekt plugin`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.AndroidLib,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                    ModuleSpec(
                        name = "b",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            detektEngine = engine,
        )
        File(projectDir, "build.gradle").appendText(
            """

            gradle.projectsEvaluated {
                subprojects.each { p ->
                    println("DETEKT-PLUGINS ${'$'}{p.path} " +
                        "v1=${'$'}{p.plugins.hasPlugin('io.gitlab.arturbosch.detekt')} " +
                        "v2=${'$'}{p.plugins.hasPlugin('dev.detekt')}")
                }
            }
            """.trimIndent(),
        )
        projectDir.initGit()

        val result = projectDir.runTasks("gitHooksSetup", "pipelineCheck", "prePushCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":pipelineCheck")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":prePushCheck")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":gitHooksSetup")?.outcome)
        val expected = if (engine == 1) "v1=true v2=false" else "v1=false v2=true"
        listOf(":a", ":b").forEach { path ->
            assertTrue(result.output.contains("DETEKT-PLUGINS $path $expected"), "$path must apply only engine $engine")
            assertTrue(
                result.tasks.any { it.path.startsWith("$path:detekt") },
                "the aggregate tasks must run $path's detekt tasks, got: ${result.tasks.map { it.path }}",
            )
        }
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `kotlin multiplatform and compose multiplatform modules analyse common sources`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "k",
                        type = ModuleType.KotlinJvm,
                        applyMultiplatformPlugin = true,
                        detektKotlinConfigContent = EMPTY_BLOCKS_CONFIG,
                        kotlinSources = mapOf("src/commonMain/kotlin/sample/Empty.kt" to EMPTY_FUNCTION_SOURCE),
                    ),
                    ModuleSpec(
                        name = "cmp",
                        type = ModuleType.KotlinJvm,
                        applyMultiplatformPlugin = true,
                        applyJetbrainsComposePlugin = true,
                        kotlinSources =
                            fixtureSources("compose-violations").mapKeys { (path, _) ->
                                path.replace("src/main/", "src/commonMain/")
                            },
                    ),
                ),
            qualityConfig = QualityConfig(extraExtensionContent = "detekt.xmlReportEnabled.set(true)"),
            detektEngine = engine,
        )

        projectDir.runTasks(":k:detekt", ":cmp:detekt", "--continue", expectFailure = true)

        assertEquals(setOf("EmptyFunctionBlock"), projectDir.reportedRuleIds("k", "detekt"))
        assertTrue(
            "PublicComposablePreview" in projectDir.reportedRuleIds("cmp", "detekt"),
            "compose rules must run on commonMain",
        )
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `a JVM-only build without AGP on the classpath is analysed`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = EMPTY_BLOCKS_CONFIG,
                        kotlinSources = mapOf("src/main/kotlin/sample/Empty.kt" to EMPTY_FUNCTION_SOURCE),
                    ),
                ),
            qualityConfig = QualityConfig(extraExtensionContent = "detekt.xmlReportEnabled.set(true)"),
            detektEngine = engine,
        )

        projectDir.runTasks(":a:detekt", expectFailure = true, withoutAgp = true)

        assertEquals(setOf("EmptyFunctionBlock"), projectDir.reportedRuleIds("a", "detekt"))
    }

    private companion object {
        val COMPOSE_DEFAULT_RULE_IDS =
            setOf("EmptyFunctionBlock", "Indentation", "InvalidPackageDeclaration", "PublicComposablePreview")

        val TYPE_RESOLUTION_CONFIG =
            """
            |kode:
            |  BlockingSqlDelightCall:
            |    active: true
            |  MissingTypeDeclaration:
            |    active: true
            """.trimMargin()

        val EMPTY_BLOCKS_CONFIG =
            """
            |empty-blocks:
            |  EmptyFunctionBlock:
            |    active: true
            """.trimMargin()

        val UNRELATED_RULE_CONFIG =
            """
            |style:
            |  MaxLineLength:
            |    active: true
            |    maxLineLength: 200
            """.trimMargin()
    }
}
