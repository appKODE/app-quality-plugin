package ru.kode.android.app.quality.plugin.foundation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.QualityConfig
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.runTasks
import java.io.File

/** Misconfigurations of the detekt engine switch and leftovers from AQP 2.x. */
class DetektEngineFailureTest {
    @TempDir
    lateinit var tempDir: File

    private val projectDir get() = File(tempDir, "test-project")

    private val kotlinModule =
        ModuleSpec(
            name = "a",
            type = ModuleType.KotlinJvm,
            kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
        )

    @ParameterizedTest(name = "detektEngine={0}")
    @ValueSource(strings = ["3", "two"])
    fun `unsupported engine value fails with the supported values`(value: String) {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule),
            gradleProperties = mapOf("ru.kode.appQuality.detektEngine" to value),
        )

        val result = projectDir.runTasks("help", expectFailure = true)

        assertTrue(result.output.contains("UNSUPPORTED DETEKT ENGINE"), "expected the engine message")
        assertTrue(result.output.contains("'ru.kode.appQuality.detektEngine' is '$value'"), "expected the value")
    }

    @Test
    fun `engine 2 without the dev detekt plugin on the classpath fails with the fix`() {
        projectDir.createQualityProject(modules = listOf(kotlinModule), detektEngine = 2, declareDetekt2Plugin = false)

        val result = projectDir.runTasks("help", expectFailure = true, withoutDetekt2Plugin = true)

        assertTrue(result.output.contains("DETEKT 2 GRADLE PLUGIN NOT FOUND"), "expected the missing plugin message")
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `a module applying the other engine's detekt plugin fails`(engine: Int) {
        val otherPluginId = if (engine == 1) "dev.detekt" else "io.gitlab.arturbosch.detekt"
        projectDir.createQualityProject(
            modules = listOf(kotlinModule),
            detektEngine = engine,
            declareDetekt2Plugin = true,
        )
        // Applied BEFORE the Kotlin plugin: the other way round the second plugin itself fails
        // first, with Gradle's "Cannot add extension with name 'detekt'".
        File(projectDir, "a/build.gradle").writeText(
            "plugins {\n    id '$otherPluginId'\n    id 'org.jetbrains.kotlin.jvm'\n}\n",
        )

        val result = projectDir.runTasks("help", expectFailure = true)

        assertTrue(result.output.contains("TWO DETEKT PLUGINS IN ONE BUILD"), "expected the two plugins message")
        assertTrue(result.output.contains("Project ':a' applies '$otherPluginId'"), "expected the offending module")
    }

    @ParameterizedTest(name = "engine {0}")
    @ValueSource(ints = [1, 2])
    fun `unresolvable rules coordinates fail naming the coordinates`(engine: Int) {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule),
            qualityConfig =
                QualityConfig(
                    extraExtensionContent = "detekt.kotlin.rules { from(\"ru.kode:detekt-rules-nonexistent:9.9.9\") }",
                ),
            detektEngine = engine,
        )

        val result = projectDir.runTasks(":a:detekt", expectFailure = true)

        assertTrue(
            result.output.contains("Could not find ru.kode:detekt-rules-nonexistent:9.9.9"),
            "expected Gradle's resolution error naming the coordinates",
        )
    }

    @Test
    fun `a leftover 2x rules jar under libs warns`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule),
            extraRootFiles = mapOf("libs/detekt-rules-1.4.0.jar" to ""),
        )

        val result = projectDir.runTasks("help")

        assertTrue(result.output.contains("LEGACY KODE RULES JAR FOUND"), "expected the legacy jar warning")
        assertTrue(result.output.contains("detekt-rules-1.4.0.jar"), "expected the jar name")
    }

    @Test
    fun `the removed defaultFiles DSL fails as an unknown property`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule),
            qualityConfig = QualityConfig(extraExtensionContent = "detekt.android.rules.defaultFiles.set([])"),
        )

        val result = projectDir.runTasks("help", expectFailure = true)

        assertTrue(result.output.contains("defaultFiles"), "expected Gradle to name the removed property")
    }
}
