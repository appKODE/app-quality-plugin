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

    // Engine 1 on AGP 9 built-in Kotlin analyses every source dir in the plain task, so androidTest
    // must leave it along with its classpath.
    @ParameterizedTest(name = "androidTest {0}")
    @ValueSource(booleans = [false, true])
    fun `with type resolution on engine 1 analyses androidTest sources only when not ignored`(androidTest: Boolean) {
        val projectDir = projectDir(1)
        val sources =
            mapOf(
                "src/main/kotlin/sample/Main.kt" to EMPTY_FUNCTION_SOURCE,
                "src/androidTest/kotlin/sample/MainTest.kt" to EMPTY_FUNCTION_SOURCE.replace("empty", "emptyTest"),
            )
        projectDir.createQualityProject(
            modules = listOf(module("app", ModuleType.AndroidApp, sources)),
            qualityConfig =
                QualityConfig(
                    detekt = DetektBlock(typeResolution = true),
                    extraExtensionContent =
                        XML + if (androidTest) "\ndetekt.ignoredTypeResolutionVariants.empty()" else "",
                ),
            detektEngine = 1,
        )
        projectDir.initGit()

        projectDir.runTasks("pipelineCheck", "--continue", "--configuration-cache", expectFailure = true)

        assertEquals(if (androidTest) 2 else 1, projectDir.findings("app", "EmptyFunctionBlock"))
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

    @ParameterizedTest(name = "engine {0}, androidTest {1}")
    @CsvSource("1, false", "1, true", "2, false", "2, true")
    fun `with type resolution on android modules run debug and its unit tests`(
        engine: Int,
        androidTest: Boolean,
    ) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    module("app", ModuleType.AndroidApp),
                    // Ignored by the default `release` entry as a substring; detekt's exact match would keep it.
                    module("lib", ModuleType.AndroidLib).copy(buildTypes = listOf("releaseGoogle")),
                    module("jvm"),
                ),
            qualityConfig =
                QualityConfig(
                    detekt = DetektBlock(typeResolution = true),
                    extraExtensionContent = if (androidTest) "detekt.ignoredTypeResolutionVariants.empty()" else "",
                ),
            detektEngine = engine,
        )
        projectDir.initGit()

        val result = projectDir.runTasks("pipelineCheck")

        val android =
            if (engine == 1) {
                // Detekt 1 has no variant tasks on AGP 9 built-in Kotlin: AQP gives the plain task a classpath.
                listOf(":app:detekt", ":lib:detekt")
            } else {
                val components = listOf("Debug", "DebugUnitTest") + if (androidTest) listOf("DebugAndroidTest") else emptyList()
                listOf("app", "lib").flatMap { m -> components.map { ":$m:detekt$it" } }
            }
        assertEquals((android + listOf(":jvm:detektMain", ":jvm:detektTest")).sorted(), result.detektTasks().sorted())
        val compiled = result.compileTasks()
        assertTrue(":app:compileDebugKotlin" in compiled && ":lib:compileDebugUnitTestKotlin" in compiled, "got: $compiled")
        assertTrue(compiled.none { "Release" in it }, "no release variant may compile, got: $compiled")
        assertEquals(androidTest, ":lib:compileDebugAndroidTestKotlin" in compiled, "got: $compiled")
    }

    @ParameterizedTest(name = "engine {0}, ignored {1}")
    @CsvSource("1, false", "1, true", "2, false", "2, true")
    fun `with type resolution on a custom build type is analysed until ignored`(
        engine: Int,
        ignored: Boolean,
    ) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules = listOf(module("lib", ModuleType.AndroidLib).copy(buildTypes = listOf("preprod"))),
            qualityConfig =
                QualityConfig(
                    detekt = DetektBlock(typeResolution = true),
                    extraExtensionContent = if (ignored) "detekt.ignoredBuildTypes.addAll(\"preprod\")" else "",
                ),
            detektEngine = engine,
        )
        projectDir.initGit()

        val result = projectDir.runTasks("pipelineCheck")

        // Preprod has no unit test component: AGP 9 only builds them for the test build type.
        val expected = listOf(":lib:detektDebug", ":lib:detektDebugUnitTest") + if (ignored) emptyList() else listOf(":lib:detektPreprod")
        assertEquals(if (engine == 1) listOf(":lib:detekt") else expected.sorted(), result.variantDetektTasks("lib"))
        assertEquals(!ignored, ":lib:compilePreprodKotlin" in result.compileTasks(), "got: ${result.compileTasks()}")
    }

    // Defaults are initial values and conventions: addAll appends, set replaces, unset restores them.
    @ParameterizedTest(name = "kotlinDsl {0}: {1}")
    @CsvSource(
        delimiter = '|',
        value = [
            "true  | detekt.ignoredBuildTypes.addAll(\"preprod\")             | Debug DebugUnitTest",
            "false | detekt.ignoredBuildTypes.addAll(\"preprod\")             | Debug DebugUnitTest",
            "true  | detekt.ignoredBuildTypes.set(listOf(\"preprod\"))        | Debug DebugUnitTest Release",
            "true  | detekt.ignoredTypeResolutionVariants.addAll(\"Preprod\") | Debug DebugUnitTest",
            "true  | detekt.ignoredTypeResolutionVariants.set(listOf(\"Preprod\")) | Debug DebugAndroidTest DebugUnitTest",
            "false | detekt.ignoredBuildTypes = [\"preprod\"]                 | Debug DebugUnitTest Release",
            "true  | detekt.ignoredBuildTypes.set(listOf(\"x\")); detekt.ignoredBuildTypes.unset() | Debug DebugUnitTest Preprod",
            "false | detekt.ignoredBuildTypes = [\"x\"]; detekt.ignoredBuildTypes = null | Debug DebugUnitTest Preprod",
        ],
    )
    fun `ignored lists keep their defaults on addAll, drop them on set and restore them on unset`(
        kotlinDsl: Boolean,
        config: String,
        components: String,
    ) {
        val projectDir = projectDir(2)
        projectDir.createQualityProject(
            modules = listOf(module("lib", ModuleType.AndroidLib).copy(buildTypes = listOf("preprod"))),
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = true), extraExtensionContent = config),
            useKotlinDsl = kotlinDsl,
            detektEngine = 2,
        )
        projectDir.initGit()

        val result = projectDir.runTasks("pipelineCheck")

        assertEquals(components.split(" ").map { ":lib:detekt$it" }.sorted(), result.variantDetektTasks("lib"))
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `with type resolution on a module without unit tests runs only its variant`(engine: Int) {
        val projectDir = projectDir(engine)
        projectDir.createQualityProject(
            modules =
                listOf(
                    module(
                        "lib",
                        ModuleType.AndroidLib,
                        extraBuildContent =
                            """
                            |androidComponents {
                            |    beforeVariants(selector().all()) {
                            |        it.hostTests.get(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable = false
                            |    }
                            |}
                            """.trimMargin(),
                    ),
                ),
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = true)),
            detektEngine = engine,
        )
        projectDir.initGit()

        val result = projectDir.runTasks("pipelineCheck")

        assertEquals(if (engine == 1) listOf(":lib:detekt") else listOf(":lib:detektDebug"), result.variantDetektTasks("lib"))
        assertTrue(result.compileTasks().none { "UnitTest" in it }, "got: ${result.compileTasks()}")
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

    @ParameterizedTest(name = "engine {0}, ruStore ignored {1}")
    @CsvSource("1, false", "1, true", "2, false", "2, true")
    fun `with type resolution on a flavored module analyses each flavor's debug variant`(
        engine: Int,
        ruStoreIgnored: Boolean,
    ) {
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
                        detektKotlinConfigContent = UNNECESSARY_SAFE_CALL_CONFIG,
                        kotlinSources = mapOf("src/main/kotlin/sample/Main.kt" to "package sample\n\nimport sel.v\n\nfun f() = v()?.length\n"),
                        extraBuildContent =
                            """
                            |android {
                            |    flavorDimensions.add("store")
                            |    productFlavors {
                            |        google { dimension = "store" }
                            |        ruStore { dimension = "store" }
                            |    }
                            |}
                            |dependencies {
                            |    debugImplementation project(':dbgdep')
                            |    releaseImplementation project(':reldep')
                            |}
                            """.trimMargin(),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt = DetektBlock(typeResolution = true),
                    extraExtensionContent =
                        XML + if (ruStoreIgnored) "\ndetekt.ignoredTypeResolutionVariants.addAll(\"ruStore\")" else "",
                ),
            detektEngine = engine,
        )
        projectDir.initGit()

        val result = projectDir.runTasks("pipelineCheck", "--continue", expectFailure = true)

        val flavors = if (ruStoreIgnored) listOf("Google") else listOf("Google", "RuStore")
        val expected = flavors.flatMap { listOf(":app:detekt${it}Debug", ":app:detekt${it}DebugUnitTest") }
        assertEquals(if (engine == 1) listOf(":app:detekt") else expected.sorted(), result.variantDetektTasks("app"))
        // Engine 1 analyses once with every selected classpath; engine 2 once per debug variant.
        assertEquals(if (engine == 1) 1 else flavors.size, projectDir.findings("app", "UnnecessarySafeCall"))
        val compiled = result.compileTasks()
        assertTrue(compiled.none { "Release" in it || "AndroidTest" in it }, "got: $compiled")
        assertEquals(!ruStoreIgnored, ":app:compileRuStoreDebugKotlin" in compiled, "got: $compiled")
    }

    @ParameterizedTest(name = "engine {0}, typeResolution {1}")
    @CsvSource("1, false", "1, true", "2, false", "2, true")
    fun `with AGP 8 and kotlin-android type resolution runs debug and its unit tests`(
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
            assertEquals(listOf(":app:detektDebug", ":app:detektDebugUnitTest"), appTasks)
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
        assertTrue(Regex("ignoredBuildTypes: +\\[debug, release]").containsMatchIn(result.output), result.output)
        assertTrue(Regex("ignoredTypeResolutionVariants: +\\[AndroidTest]").containsMatchIn(result.output), result.output)
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
