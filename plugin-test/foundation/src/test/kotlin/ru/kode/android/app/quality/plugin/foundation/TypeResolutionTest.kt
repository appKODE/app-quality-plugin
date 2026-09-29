package ru.kode.android.app.quality.plugin.foundation

import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import ru.kode.android.app.quality.plugin.test.utils.DetektBlock
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.QualityConfig
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.initGit
import ru.kode.android.app.quality.plugin.test.utils.resolveRequiredAgpJars
import ru.kode.android.app.quality.plugin.test.utils.runTasks
import java.io.File

/**
 * Which detekt tasks `pipelineCheck` runs, and what they see, with `detekt.typeResolution` off
 * and on. Every test runs once per engine.
 */
class TypeResolutionTest {
    @TempDir
    lateinit var tempDir: File

    private fun projectDir(
        engine: Int,
        name: String = "p",
    ) = File(tempDir, "$name$engine")

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `with type resolution off pipelineCheck runs only the plain detekt tasks`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules = listOf(module("app", ModuleType.AndroidApp), module("lib", ModuleType.AndroidLib), module("jvm")),
            detektEngine = engine,
        )
        projectDir.initGit()

        val result = projectDir.runTasks("pipelineCheck")

        assertEquals(listOf(":app:detekt", ":jvm:detekt", ":lib:detekt"), result.detektTasks().sorted())
        assertTrue(result.compileTasks().isEmpty(), "nothing must compile, got: ${result.compileTasks()}")
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `with type resolution off each file is analysed once`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createEmptyFunctionProject(engine, typeResolution = false)

        projectDir.runTasks("pipelineCheck", "--continue", expectFailure = true)

        assertEquals(2, projectDir.findings("jvm", "EmptyFunctionBlock"))
        assertEquals(2, projectDir.findings("app", "EmptyFunctionBlock"))
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `with type resolution on a JVM module runs detektMain and detektTest`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules = listOf(module("jvm")),
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = true)),
            detektEngine = engine,
        )
        projectDir.initGit()

        val result = projectDir.runTasks("pipelineCheck")

        assertEquals(listOf(":jvm:detektMain", ":jvm:detektTest"), result.detektTasks().sorted())
        assertTrue(":jvm:compileKotlin" in result.compileTasks(), "got: ${result.compileTasks()}")
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `with type resolution on each file is analysed once`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createEmptyFunctionProject(engine, typeResolution = true)

        projectDir.runTasks("pipelineCheck", "--continue", expectFailure = true)

        assertEquals(2, projectDir.findings("jvm", "EmptyFunctionBlock"))
        assertEquals(2, projectDir.findings("app", "EmptyFunctionBlock"))
    }

    @ParameterizedTest(name = "engine {0}, typeResolution {1}")
    @CsvSource("1, false", "1, true", "2, false", "2, true")
    fun `types from project dependencies resolve in main and test sources`(
        engine: Int,
        typeResolution: Boolean,
    ) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    DEP,
                    // `?.` on a non-null String is only visible with `:dep` on the classpath.
                    module(
                        "mainuser",
                        sources = mapOf("src/main/kotlin/sample/Main.kt" to DBG_USAGE),
                        extraBuildContent = "dependencies { implementation project(':dep') }",
                    ),
                    module(
                        "testuser",
                        sources = mapOf("src/test/kotlin/sample/MainTest.kt" to DBG_USAGE),
                        extraBuildContent = "dependencies { testImplementation project(':dep') }",
                    ),
                ).map { it.copy(detektKotlinConfigContent = UNNECESSARY_SAFE_CALL_CONFIG) },
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = typeResolution), extraExtensionContent = XML),
            detektEngine = engine,
        )
        projectDir.initGit()

        projectDir.runTasks("pipelineCheck", "--continue", expectFailure = typeResolution)

        val expected = if (typeResolution) 1 else 0
        assertEquals(expected, projectDir.findings("mainuser", "UnnecessarySafeCall"))
        assertEquals(expected, projectDir.findings("testuser", "UnnecessarySafeCall"))
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `with type resolution on android modules run detektMain and detektTest`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules = listOf(module("app", ModuleType.AndroidApp), module("lib", ModuleType.AndroidLib), module("jvm")),
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = true)),
            detektEngine = engine,
        )
        projectDir.initGit()

        val result = projectDir.runTasks("pipelineCheck")

        val android =
            if (engine == 1) {
                // Detekt 1 has no variant tasks on AGP 9 built-in Kotlin: AQP gives the plain task a classpath.
                listOf(":app:detekt", ":lib:detekt")
            } else {
                listOf("app", "lib").flatMap { m ->
                    listOf("Main", "Test", "Debug", "DebugAndroidTest", "DebugUnitTest").map { ":$m:detekt$it" }
                }
            }
        assertEquals((android + listOf(":jvm:detektMain", ":jvm:detektTest")).sorted(), result.detektTasks().sorted())
        assertTrue(":app:compileDebugKotlin" in result.compileTasks(), "got: ${result.compileTasks()}")
    }

    @ParameterizedTest(name = "engine {0}, typeResolution {1}")
    @CsvSource("1, false", "1, true", "2, false", "2, true")
    fun `types from project dependencies resolve in android main and unit test sources`(
        engine: Int,
        typeResolution: Boolean,
    ) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    DEP,
                    module(
                        "mainuser",
                        ModuleType.AndroidApp,
                        sources = mapOf("src/main/kotlin/sample/Main.kt" to DBG_USAGE, "src/main/kotlin/sample/Ctx.kt" to CONTEXT_USAGE),
                        extraBuildContent = "dependencies { implementation project(':dep') }",
                    ),
                    module(
                        "testuser",
                        ModuleType.AndroidLib,
                        sources = mapOf("src/test/kotlin/sample/MainTest.kt" to DBG_USAGE),
                        extraBuildContent = "dependencies { testImplementation project(':dep') }",
                    ),
                ).map { it.copy(detektKotlinConfigContent = UNNECESSARY_SAFE_CALL_CONFIG) },
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = typeResolution), extraExtensionContent = XML),
            detektEngine = engine,
        )
        projectDir.initGit()

        projectDir.runTasks("pipelineCheck", "--continue", expectFailure = typeResolution)

        val expected = if (typeResolution) 1 else 0
        // DBG_USAGE plus CONTEXT_USAGE, which needs android.jar on the classpath.
        assertEquals(expected * 2, projectDir.findings("mainuser", "UnnecessarySafeCall"))
        assertEquals(expected, projectDir.findings("testuser", "UnnecessarySafeCall"))
    }

    @ParameterizedTest(name = "engine {0}, ignoredBuildTypes {1}")
    @CsvSource("1, ''", "1, debug;staging", "2, ''", "2, debug;staging")
    fun `ignoredBuildTypes pick the analysed variants`(
        engine: Int,
        ignoredBuildTypes: String,
    ) {
        val ignored = ignoredBuildTypes.split(";").filter { it.isNotEmpty() }
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    // Both expose sel.v(); only the debug one returns a non-null String.
                    dep("dbgdep", "package sel\n\nfun v(): String = System.lineSeparator()\n"),
                    dep("reldep", "package sel\n\nfun v(): String? = System.getenv(\"V\")\n"),
                    ModuleSpec(
                        name = "app",
                        type = ModuleType.AndroidApp,
                        buildTypes = listOf("staging"),
                        detektKotlinConfigContent = UNNECESSARY_SAFE_CALL_CONFIG,
                        kotlinSources = mapOf("src/main/kotlin/sample/Main.kt" to "package sample\n\nimport sel.v\n\nfun f() = v()?.length\n"),
                        extraBuildContent =
                            """
                            |android {
                            |    flavorDimensions.add("env")
                            |    productFlavors {
                            |        dev { dimension = "env" }
                            |        prod { dimension = "env" }
                            |    }
                            |}
                            |dependencies {
                            |    debugImplementation project(':dbgdep')
                            |    releaseImplementation project(':reldep')
                            |    stagingImplementation project(':reldep')
                            |}
                            """.trimMargin(),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt = DetektBlock(typeResolution = true, ignoredBuildTypes = ignored.ifEmpty { null }),
                    extraExtensionContent = XML,
                ),
            detektEngine = engine,
        )
        projectDir.initGit()

        val result = projectDir.runTasks("pipelineCheck", "--continue", expectFailure = ignored.isEmpty())

        val appTasks = result.variantDetektTasks("app")
        if (ignored.isEmpty()) {
            // Default ignoredBuildTypes keep debug and staging. Engine 1 analyses one variant, debug
            // first (devDebug alphabetically); engine 2 every flavor of both, so both debug variants report.
            val variants = listOf("DevDebug", "DevStaging", "ProdDebug", "ProdStaging")
            val tests = listOf("DevDebugAndroidTest", "DevDebugUnitTest", "ProdDebugAndroidTest", "ProdDebugUnitTest")
            val expected = (variants + tests).map { ":app:detekt$it" }.sorted()
            assertEquals(if (engine == 1) listOf(":app:detekt") else expected, appTasks)
            assertEquals(if (engine == 1) 1 else 2, projectDir.findings("app", "UnnecessarySafeCall"))
        } else {
            // Release has no test components: AGP 9 only builds them for the test build type.
            val expected = listOf(":app:detektDevRelease", ":app:detektProdRelease")
            assertEquals(if (engine == 1) listOf(":app:detekt") else expected, appTasks)
            assertEquals(0, projectDir.findings("app", "UnnecessarySafeCall"))
        }
    }

    @ParameterizedTest(name = "engine {0}, typeResolution {1}")
    @CsvSource("1, false", "1, true", "2, false", "2, true")
    fun `with AGP 8 and kotlin-android type resolution runs detektMain and detektTest`(
        engine: Int,
        typeResolution: Boolean,
    ) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    DEP,
                    ModuleSpec(
                        name = "app",
                        type = ModuleType.AndroidApp,
                        compileSdk = 35,
                        applyKotlinAndroidPlugin = true,
                        kotlinSources =
                            mapOf("src/main/kotlin/sample/Main.kt" to DBG_USAGE, "src/main/kotlin/sample/Ctx.kt" to CONTEXT_USAGE),
                        extraBuildContent = "dependencies { implementation project(':dep') }",
                    ),
                ).map { it.copy(detektKotlinConfigContent = UNNECESSARY_SAFE_CALL_CONFIG) },
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = typeResolution), extraExtensionContent = XML),
            detektEngine = engine,
        )
        projectDir.initGit()

        val result =
            projectDir.runTasks(
                "pipelineCheck",
                "--continue",
                agpClasspath = resolveRequiredAgpJars(LEGACY_AGP_VERSION),
                gradleVersion = LEGACY_GRADLE_VERSION,
                expectFailure = typeResolution,
            )

        val appTasks = result.variantDetektTasks("app")
        if (typeResolution) {
            assertEquals(listOf(":app:detektDebug", ":app:detektDebugAndroidTest", ":app:detektDebugUnitTest"), appTasks)
            assertEquals(2, projectDir.findings("app", "UnnecessarySafeCall"))
        } else {
            assertEquals(listOf(":app:detekt"), appTasks)
            assertEquals(0, projectDir.findings("app", "UnnecessarySafeCall"))
        }
    }

    @ParameterizedTest(name = "engine {0}, legacy AGP {1}")
    @CsvSource("1, false", "1, true", "2, false", "2, true")
    fun `with type resolution on and every build type ignored pipelineCheck fails`(
        engine: Int,
        legacyAgp: Boolean,
    ) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "app",
                        type = ModuleType.AndroidApp,
                        compileSdk = 35,
                        applyKotlinAndroidPlugin = legacyAgp,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig =
                QualityConfig(detekt = DetektBlock(typeResolution = true, ignoredBuildTypes = listOf("debug", "release"))),
            detektEngine = engine,
        )
        projectDir.initGit()

        val result =
            if (legacyAgp) {
                projectDir.runTasks(
                    "pipelineCheck",
                    agpClasspath = resolveRequiredAgpJars(LEGACY_AGP_VERSION),
                    gradleVersion = LEGACY_GRADLE_VERSION,
                    expectFailure = true,
                )
            } else {
                projectDir.runTasks("pipelineCheck", expectFailure = true)
            }

        assertTrue("TYPE RESOLUTION: NOTHING TO ANALYSE" in result.output, result.output)
        assertTrue("Project ':app'" in result.output, result.output)
        assertTrue("ignoredBuildTypes: [debug, release]" in result.output, result.output)
    }

    @ParameterizedTest(name = "engine {0}, legacy AGP {1}")
    @CsvSource("1, false", "1, true", "2, false", "2, true")
    fun `with type resolution on a Java-only android module runs the plain task`(
        engine: Int,
        legacyAgp: Boolean,
    ) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    // No Kotlin plugin, yet a .kt file on detekt's source path so the plain task runs.
                    ModuleSpec(
                        name = "app",
                        type = ModuleType.AndroidApp,
                        compileSdk = 35,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = true)),
            gradleProperties = if (legacyAgp) emptyMap() else mapOf("android.builtInKotlin" to "false"),
            detektEngine = engine,
        )
        projectDir.initGit()

        val result =
            if (legacyAgp) {
                projectDir.runTasks(
                    "pipelineCheck",
                    agpClasspath = resolveRequiredAgpJars(LEGACY_AGP_VERSION),
                    gradleVersion = LEGACY_GRADLE_VERSION,
                )
            } else {
                projectDir.runTasks("pipelineCheck")
            }

        assertEquals(listOf(":app:detekt"), result.detektTasks())
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `with type resolution on a multiplatform module runs the plain task and warns once`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "shared",
                        type = ModuleType.KotlinJvm,
                        applyMultiplatformPlugin = true,
                        kotlinSources = mapOf("src/jvmMain/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = true)),
            detektEngine = engine,
        )
        projectDir.initGit()

        val result = projectDir.runTasks("pipelineCheck")

        assertEquals(listOf(":shared:detekt"), result.detektTasks())
        assertEquals(1, Regex("TYPE RESOLUTION NOT SUPPORTED").findAll(result.output).count(), result.output)
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `with type resolution on pipelineCheck reuses the configuration cache`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    DEP,
                    module("app", ModuleType.AndroidApp, mapOf("src/main/kotlin/sample/Main.kt" to DBG_USAGE), DEP_BUILD),
                    module("jvm", sources = mapOf("src/main/kotlin/sample/Main.kt" to DBG_USAGE), extraBuildContent = DEP_BUILD),
                ).map { it.copy(detektKotlinConfigContent = UNNECESSARY_SAFE_CALL_CONFIG) },
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = true), extraExtensionContent = XML),
            detektEngine = engine,
        )
        projectDir.initGit()
        val arguments = listOf("--configuration-cache")

        projectDir.runTasks("clean", "pipelineCheck", "--continue", arguments = arguments, expectFailure = true)
        val second = projectDir.runTasks("clean", "pipelineCheck", "--continue", arguments = arguments, expectFailure = true)

        assertTrue("Reusing configuration cache" in second.output, second.output)
        assertEquals(1, projectDir.findings("app", "UnnecessarySafeCall"))
        assertEquals(1, projectDir.findings("jvm", "UnnecessarySafeCall"))
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `a baseline created by the plain task applies with type resolution on`(engine: Int) {
        val projectDir = projectDir(engine)
        val sources = mapOf("src/main/kotlin/sample/Main.kt" to EMPTY_FUNCTION_SOURCE)
        projectDir.createQualityProject(
            modules = listOf(module("app", ModuleType.AndroidApp, sources), module("jvm", sources = sources)),
            qualityConfig =
                QualityConfig(
                    detekt = DetektBlock(typeResolution = true),
                    extraExtensionContent = "detekt.baseline.set(rootProject.layout.projectDirectory.file(\"detekt-baseline.xml\"))",
                ),
            detektEngine = engine,
        )
        projectDir.initGit()

        projectDir.runTasks(":app:detektBaseline", ":jvm:detektBaseline")
        val result = projectDir.runTasks("pipelineCheck")

        assertTrue(File(projectDir, "app/detekt-baseline.xml").exists() && File(projectDir, "jvm/detekt-baseline.xml").exists())
        assertTrue(":jvm:detektMain" in result.detektTasks(), "got: ${result.detektTasks()}")
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `detekt's own test task keeps its own sources`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules = listOf(module("jvm", sources = mapOf("src/main/kotlin/sample/Main.kt" to EMPTY_FUNCTION_SOURCE))),
            qualityConfig = QualityConfig(extraExtensionContent = XML),
            detektEngine = engine,
        )
        projectDir.initGit()

        projectDir.runTasks(":jvm:detektTest")

        assertEquals(0, projectDir.findings("jvm", "EmptyFunctionBlock"))
    }

    private fun File.createEmptyFunctionProject(
        engine: Int,
        typeResolution: Boolean,
    ) {
        val sources =
            mapOf(
                "src/main/kotlin/sample/Main.kt" to EMPTY_FUNCTION_SOURCE,
                "src/test/kotlin/sample/MainTest.kt" to EMPTY_FUNCTION_SOURCE.replace("empty", "emptyTest"),
            )
        createQualityProject(
            modules = listOf(module("app", ModuleType.AndroidApp, sources), module("jvm", sources = sources)),
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = typeResolution), extraExtensionContent = XML),
            detektEngine = engine,
        )
        initGit()
    }
}

private const val XML = "detekt.xmlReportEnabled.set(true)"

/** `?.` on a non-null String: only visible with `:dep` on the classpath. */
internal const val DBG_USAGE = "package sample\n\nimport dep.dbg\n\nfun f() = dbg()?.length\n"

/** `?.` on a non-null parameter: only visible with android.jar on the classpath. */
private const val CONTEXT_USAGE = "package sample\n\nimport android.content.Context\n\nfun g(c: Context) = c?.hashCode()\n"

private const val DEP_BUILD = "dependencies { implementation project(':dep') }"

internal val DEP = dep("dep", "package dep\n\nfun dbg(): String = System.lineSeparator()\n")

/**
 * A JVM module holding [source]. It is compiled as Kotlin 2.1: detekt 1.23.8's Kotlin 2.0
 * frontend silently fails to read newer class metadata, so its types would stay unresolved.
 */
private fun dep(
    name: String,
    source: String,
) = module(
    name,
    sources = mapOf("src/main/kotlin/${source.substringAfter("package ").substringBefore("\n")}/Dep.kt" to source),
    extraBuildContent =
        """
        |kotlin {
        |    compilerOptions {
        |        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1
        |        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1
        |    }
        |}
        """.trimMargin(),
)

/** `Configs.DETEKT_UNNECESSARY_SAFE_CALL` without the `build:` block detekt 2 rejects. */
internal const val UNNECESSARY_SAFE_CALL_CONFIG = "potential-bugs:\n  UnnecessarySafeCall:\n    active: true\n"

private fun module(
    name: String,
    type: ModuleType = ModuleType.KotlinJvm,
    sources: Map<String, String> = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
    extraBuildContent: String = "",
) = ModuleSpec(name = name, type = type, kotlinSources = sources, extraBuildContent = extraBuildContent)

private fun BuildResult.detektTasks() = tasks.map { it.path }.filter { ":detekt" in it }

/** [module]'s detekt tasks that analyse, without the `detektMain`/`detektTest` lifecycle tasks. */
private fun BuildResult.variantDetektTasks(module: String) =
    detektTasks().filter { it.startsWith(":$module:") && it !in listOf(":$module:detektMain", ":$module:detektTest") }.sorted()

private fun BuildResult.compileTasks() = tasks.map { it.path }.filter { Regex(":compile\\w*Kotlin$").containsMatchIn(it) }

/** How many times [rule] is reported across all of [module]'s detekt XML reports. */
internal fun File.findings(
    module: String,
    rule: String,
): Int =
    File(this, "$module/build/reports/detekt")
        .listFiles { file -> file.extension == "xml" }
        .orEmpty()
        .sumOf { report -> Regex("""source="detekt\.$rule"""").findAll(report.readText()).count() }
