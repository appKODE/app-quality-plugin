package ru.kode.android.app.quality.plugin.foundation

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.kode.android.app.quality.plugin.test.utils.DetektBlock
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.QualityConfig
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.getFile
import ru.kode.android.app.quality.plugin.test.utils.initGit
import ru.kode.android.app.quality.plugin.test.utils.runTasks
import java.io.File

class ConfigurationCacheTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        projectDir = File(tempDir, "test-project")
    }

    @Test
    fun `second pipelineCheck run reuses the configuration cache`() {
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
        projectDir.initGit()

        val first = projectDir.runTasks("pipelineCheck", arguments = listOf("--configuration-cache"))
        assertEquals(TaskOutcome.SUCCESS, first.task(":pipelineCheck")?.outcome)

        val second = projectDir.runTasks("pipelineCheck", arguments = listOf("--configuration-cache"))
        assertTrue(
            second.output.contains("Reusing configuration cache"),
            "second run must reuse the configuration cache",
        )
    }

    @Test
    fun `creating a module detekt config file after a cached run is picked up`() {
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
        projectDir.initGit()

        // Bundled default (120 max) -> passes and stores the configuration cache.
        val first = projectDir.runTasks(":a:detekt", arguments = listOf("--configuration-cache"))
        assertEquals(TaskOutcome.SUCCESS, first.task(":a:detekt")?.outcome)

        // A stricter module config appears -> the next run must use it and fail.
        projectDir.getFile("a/detekt-kotlin-config.yml").writeText(Configs.DETEKT_MAX_LINE_60)

        val second =
            projectDir.runTasks(
                ":a:detekt",
                arguments = listOf("--configuration-cache"),
                expectFailure = true,
            )
        assertEquals(TaskOutcome.FAILED, second.task(":a:detekt")?.outcome)
        assertTrue(
            second.output.contains("MaxLineLength"),
            "expected the newly created module config to be applied",
        )
    }

    @Test
    fun `second detekt-only run reuses the configuration cache`() {
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
        projectDir.initGit()

        val first = projectDir.runTasks(":a:detekt", arguments = listOf("--configuration-cache"))
        assertEquals(TaskOutcome.SUCCESS, first.task(":a:detekt")?.outcome)

        val second = projectDir.runTasks(":a:detekt", arguments = listOf("--configuration-cache"))
        assertTrue(
            second.output.contains("Reusing configuration cache"),
            "second detekt-only run must reuse the configuration cache",
        )
    }

    @Test
    fun `second ktlintCheck-only run reuses the configuration cache`() {
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
        projectDir.initGit()

        val first = projectDir.runTasks("ktlintCheck", arguments = listOf("--configuration-cache"))
        assertEquals(TaskOutcome.SUCCESS, first.task(":ktlintCheck")?.outcome)

        val second = projectDir.runTasks("ktlintCheck", arguments = listOf("--configuration-cache"))
        assertTrue(
            second.output.contains("Reusing configuration cache"),
            "second ktlintCheck-only run must reuse the configuration cache",
        )
    }

    @Test
    fun `second run with typeResolution enabled reuses the configuration cache`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig = QualityConfig(detekt = DetektBlock(typeResolution = true)),
        )
        projectDir.initGit()

        val first = projectDir.runTasks(":a:detekt", arguments = listOf("--configuration-cache"))
        assertEquals(TaskOutcome.SUCCESS, first.task(":a:detekt")?.outcome)

        val second = projectDir.runTasks(":a:detekt", arguments = listOf("--configuration-cache"))
        assertTrue(
            second.output.contains("Reusing configuration cache"),
            "second run with typeResolution=true must reuse the configuration cache",
        )
    }
}
