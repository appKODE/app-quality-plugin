package ru.kode.android.app.quality.plugin.foundation

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.runTask
import ru.kode.android.app.quality.plugin.test.utils.runTaskWithFail
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
}
