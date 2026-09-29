package ru.kode.android.app.quality.plugin.foundation

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import ru.kode.android.app.quality.plugin.test.utils.DEFAULT_GRADLE_VERSION
import ru.kode.android.app.quality.plugin.test.utils.DetektBlock
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.QualityConfig
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.initGit
import ru.kode.android.app.quality.plugin.test.utils.resolveKotlinGradlePluginJars
import ru.kode.android.app.quality.plugin.test.utils.resolveRequiredAgpJars
import ru.kode.android.app.quality.plugin.test.utils.runTasks
import java.io.File

const val MIN_GRADLE_VERSION = LEGACY_GRADLE_VERSION
const val MIN_AGP_VERSION = LEGACY_AGP_VERSION

/** detekt 2 (alpha.6) needs KGP's KotlinJvmExtension, absent from KGP 2.0. */
val MIN_KOTLIN_VERSION = mapOf(1 to "2.0.21", 2 to "2.1.21")
private const val LATEST_AGP_VERSION = "9.4.1"
private const val LATEST_KOTLIN_VERSION = "2.4.20"

/** One engine x Gradle x AGP x Kotlin x JDK combination; a null version means the test default. */
data class CompatibilityCell(
    val engine: Int,
    val gradle: String = DEFAULT_GRADLE_VERSION,
    val agp: String = LATEST_AGP_VERSION,
    val kotlin: String = LATEST_KOTLIN_VERSION,
    val jdk: Int? = null,
    val kotlinDsl: Boolean = false,
) {
    override fun toString() =
        "engine $engine, Gradle $gradle, AGP $agp, Kotlin $kotlin, JDK ${jdk ?: "default"}" +
            if (kotlinDsl) ", kts" else ""
}

/**
 * The minimum supported versions on both engines. Part of the PR tier; the latest versions are
 * covered there by [DetektEngineTest].
 */
class MinimumVersionsTest {
    @TempDir
    lateinit var tempDir: File

    @ParameterizedTest(name = "{0}")
    @MethodSource("cells")
    fun `a clean module passes and the kode rules fire`(cell: CompatibilityCell) = tempDir.assertCompatible(cell)

    companion object {
        @JvmStatic
        fun cells() =
            listOf(1, 2).flatMap { engine ->
                listOf(false, true).map { kts ->
                    CompatibilityCell(
                        engine,
                        MIN_GRADLE_VERSION,
                        MIN_AGP_VERSION,
                        MIN_KOTLIN_VERSION.getValue(engine),
                        kotlinDsl = kts,
                    )
                }
            }
    }
}

/** The full compatibility matrix — `./gradlew -p plugin-test :foundation:matrixTest`. */
@Tag("matrix")
class CompatibilityMatrixTest {
    @TempDir
    lateinit var tempDir: File

    @ParameterizedTest(name = "{0}")
    @MethodSource("cells")
    fun `a clean module passes and the kode rules fire`(cell: CompatibilityCell) = tempDir.assertCompatible(cell)

    @ParameterizedTest(name = "{0}")
    @MethodSource("typeResolutionCells")
    fun `type resolution sees project dependencies`(cell: CompatibilityCell) = tempDir.assertTypeResolution(cell)

    companion object {
        @JvmStatic
        fun typeResolutionCells() = cells().filter { it.jdk == null }

        // AGP 9 needs Gradle 9 and KGP 2.2.10+; AGP 9.4 needs Gradle 9.6+.
        @JvmStatic
        fun cells() =
            listOf(1, 2).flatMap { engine ->
                listOf(
                    CompatibilityCell(engine, MIN_GRADLE_VERSION, MIN_AGP_VERSION, MIN_KOTLIN_VERSION.getValue(engine)),
                    CompatibilityCell(engine, MIN_GRADLE_VERSION, "8.13.2", "2.2.20"),
                    CompatibilityCell(engine, "9.6.0", LATEST_AGP_VERSION, "2.3.21"),
                    CompatibilityCell(engine, DEFAULT_GRADLE_VERSION, "9.0.1", "2.3.21"),
                    CompatibilityCell(engine),
                    CompatibilityCell(engine, jdk = NEWER_JDK),
                )
            }
    }
}

/** No JDK 21 on the reference machine; 22 is the newer LTS-or-later JDK the matrix runs on. */
private const val NEWER_JDK = 22

private fun File.assertCompatible(cell: CompatibilityCell) {
    val agp8 = cell.agp.startsWith("8.")
    val projectDir = File(this, "project")
    projectDir.createQualityProject(
        modules =
            listOf(
                ModuleSpec(
                    name = "lib",
                    type = ModuleType.AndroidLib,
                    applyKotlinAndroidPlugin = agp8,
                    compileSdk = if (agp8) 35 else 36,
                    kotlinSources = fixtureSources("kode-violations"),
                ),
                ModuleSpec(
                    name = "jvm",
                    type = ModuleType.KotlinJvm,
                    kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                ),
            ),
        qualityConfig = QualityConfig(extraExtensionContent = "detekt.xmlReportEnabled.set(true)"),
        useKotlinDsl = cell.kotlinDsl,
        detektEngine = cell.engine,
    )

    val result = projectDir.runFailingCell(cell, ":jvm:detekt", ":lib:detekt")

    assertEquals(TaskOutcome.SUCCESS, result.task(":jvm:detekt")?.outcome)
    assertEquals(TaskOutcome.FAILED, result.task(":lib:detekt")?.outcome)
    assertEquals(ANDROID_DEFAULT_RULE_IDS.getValue(cell.engine), projectDir.reportedRuleIds("lib", "detekt"))
}

/** With type resolution on, pipelineCheck resolves an Android module's project dependency. */
private fun File.assertTypeResolution(cell: CompatibilityCell) {
    val agp8 = cell.agp.startsWith("8.")
    val projectDir = File(this, "project")
    projectDir.createQualityProject(
        modules =
            listOf(
                // KGP 2.0 compiles KOTLIN_2_1 as pre-release, which detekt can't read; its default 2.0 is fine.
                if (cell.kotlin.startsWith("2.0.")) DEP.copy(extraBuildContent = "") else DEP,
                ModuleSpec(
                    name = "user",
                    type = ModuleType.AndroidLib,
                    applyKotlinAndroidPlugin = agp8,
                    compileSdk = if (agp8) 35 else 36,
                    kotlinSources = mapOf("src/main/kotlin/sample/Main.kt" to DBG_USAGE),
                    extraBuildContent = "dependencies { implementation project(':dep') }",
                ),
            ).map { it.copy(detektKotlinConfigContent = UNNECESSARY_SAFE_CALL_CONFIG) },
        qualityConfig =
            QualityConfig(
                detekt = DetektBlock(typeResolution = true),
                extraExtensionContent = "detekt.xmlReportEnabled.set(true)",
            ),
        detektEngine = cell.engine,
    )
    projectDir.initGit()

    projectDir.runFailingCell(cell, "pipelineCheck")

    assertEquals(1, projectDir.findings("user", "UnnecessarySafeCall"))
}

private fun File.runFailingCell(
    cell: CompatibilityCell,
    vararg tasks: String,
) = runTasks(
    *tasks,
    "--continue",
    arguments =
        listOf("--configuration-cache") +
            cell.jdk?.let { listOf("-Dorg.gradle.java.home=${jdkHome(it)}") }.orEmpty(),
    agpClasspath = if (cell.agp == LATEST_AGP_VERSION) emptyList() else resolveRequiredAgpJars(cell.agp),
    kotlinClasspath =
        if (cell.kotlin == LATEST_KOTLIN_VERSION) emptyList() else resolveKotlinGradlePluginJars(cell.kotlin),
    gradleVersion = cell.gradle,
    expectFailure = true,
)

/** `JAVA_HOME_<n>_X64`/`_ARM64` as set by actions/setup-java, else macOS `java_home`. */
private fun jdkHome(version: Int): String =
    listOf("X64", "ARM64").firstNotNullOfOrNull { arch -> System.getenv("JAVA_HOME_${version}_$arch") }
        ?: ProcessBuilder("/usr/libexec/java_home", "-v", "$version")
            .start()
            .inputStream
            .bufferedReader()
            .readText()
            .trim()
            .takeIf { it.isNotEmpty() && File(it).isDirectory }
        ?: error("JDK $version not found: set JAVA_HOME_${version}_X64")
