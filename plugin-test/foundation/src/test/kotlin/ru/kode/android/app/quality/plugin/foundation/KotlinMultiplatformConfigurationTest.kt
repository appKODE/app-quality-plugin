package ru.kode.android.app.quality.plugin.foundation

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.resolveRequiredAgpJars
import ru.kode.android.app.quality.plugin.test.utils.runTask
import ru.kode.android.app.quality.plugin.test.utils.runTaskWithFail
import ru.kode.android.app.quality.plugin.test.utils.runTasks
import java.io.File

/**
 * Covers `org.jetbrains.kotlin.multiplatform` as a Kotlin detekt-platform trigger — a module
 * applying it (instead of `org.jetbrains.kotlin.jvm`) must still get the plugin's Kotlin
 * detekt layer configured.
 */
class KotlinMultiplatformConfigurationTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        projectDir = File(tempDir, "test-project")
    }

    @Test
    fun `kotlin multiplatform module gets the Kotlin detekt layer configured`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "shared",
                        type = ModuleType.KotlinJvm,
                        applyMultiplatformPlugin = true,
                        detektKotlinConfigContent = Configs.DETEKT_MAX_LINE_60,
                        kotlinSources = mapOf("src/jvmMain/kotlin/ru/kode/test/Long.kt" to Sources.LONG_LINE_80),
                    ),
                ),
        )

        val result = projectDir.runTaskWithFail(":shared:detekt")

        assertEquals(TaskOutcome.FAILED, result.task(":shared:detekt")?.outcome)
        assertTrue(result.output.contains("MaxLineLength"), "expected the module's detekt config to be applied")
    }

    @Test
    fun `kotlin multiplatform module passes with the bundled default config`() {
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
        )

        val result = projectDir.runTask(":shared:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":shared:detekt")?.outcome)
    }

    // Regression guard for the AGP/KMP variant-task override bug: detekt-gradle-plugin's own
    // lazy variant-registration callback reassigns `task.source` from the AGP variant's
    // sourceSets AFTER this plugin's own fileTree(include/exclude) assignment, which already
    // treats build/generated/... as a first-class source root. Only a lazy, absolute-path-based
    // `exclude(Spec)` (DetektWiring.kt) survives that later override.
    @Test
    fun `KSP-generated source under a KMP Android-target variant is excluded from detektAndroidDebug`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "shared",
                        type = ModuleType.AndroidLib,
                        applyMultiplatformPlugin = true,
                        detektAndroidConfigContent = Configs.DETEKT_MAX_LINE_60,
                        kotlinSources = mapOf("src/androidMain/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
        )
        // Simulate KSP's output dir being registered as a source root by AGP's variant API —
        // this file violates the module's own max-line-length rule, so if the exclude ever
        // stops holding, detektAndroidDebug fails on it.
        val generatedFile =
            File(
                projectDir,
                "shared/build/generated/ksp/androidDebug/kotlin/ru/kode/test/Generated.kt",
            )
        generatedFile.parentFile.mkdirs()
        generatedFile.writeText(Sources.LONG_LINE_80)

        val result =
            projectDir.runTasks(
                ":shared:detektAndroidDebug",
                agpClasspath = resolveRequiredAgpJars(LEGACY_AGP_VERSION),
                gradleVersion = LEGACY_GRADLE_VERSION,
            )

        assertEquals(TaskOutcome.SUCCESS, result.task(":shared:detektAndroidDebug")?.outcome)
        assertFalse(
            result.output.contains("Generated.kt"),
            "expected the KSP-generated file to be excluded from analysis entirely, got: ${result.output}",
        )
    }
}
