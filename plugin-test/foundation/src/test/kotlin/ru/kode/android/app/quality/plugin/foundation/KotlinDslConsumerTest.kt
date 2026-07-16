package ru.kode.android.app.quality.plugin.foundation

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.kode.android.app.quality.plugin.test.utils.KtlintBlock
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.QualityConfig
import ru.kode.android.app.quality.plugin.test.utils.SourcePatternsSlot
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.initGit
import ru.kode.android.app.quality.plugin.test.utils.runTask
import java.io.File

/**
 * Groovy scripts only ever hit the extension's Closure overloads. A real `.gradle.kts`
 * consumer exercises the `Action<T>` overloads instead — this is the only test in the suite
 * that generates and runs real Kotlin-DSL build scripts.
 */
class KotlinDslConsumerTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        projectDir = File(tempDir, "test-project")
    }

    @Test
    fun `Kotlin DSL consumer project passes pipelineCheck end to end`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    verboseLogging = true,
                    ktlint = KtlintBlock(sources = SourcePatternsSlot(include = listOf("**/test/**"))),
                ),
            useKotlinDsl = true,
        )
        projectDir.initGit()

        assertTrue(File(projectDir, "settings.gradle.kts").exists(), "expected a settings.gradle.kts file")
        assertTrue(File(projectDir, "build.gradle.kts").exists(), "expected a root build.gradle.kts file")
        assertTrue(File(projectDir, "a/build.gradle.kts").exists(), "expected the module's build.gradle.kts file")

        val result = projectDir.runTask("pipelineCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":pipelineCheck")?.outcome)
    }
}
