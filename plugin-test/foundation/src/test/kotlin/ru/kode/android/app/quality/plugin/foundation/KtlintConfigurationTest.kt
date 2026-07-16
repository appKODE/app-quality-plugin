package ru.kode.android.app.quality.plugin.foundation

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.kode.android.app.quality.plugin.test.utils.DependencySlot
import ru.kode.android.app.quality.plugin.test.utils.KtlintBlock
import ru.kode.android.app.quality.plugin.test.utils.LibsCatalog
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.QualityConfig
import ru.kode.android.app.quality.plugin.test.utils.SourcePatternsSlot
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.runTask
import ru.kode.android.app.quality.plugin.test.utils.runTaskWithFail
import java.io.File

class KtlintConfigurationTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        projectDir = File(tempDir, "test-project")
    }

    @Test
    fun `custom projectConfig editorconfig is used - 4-space source passes under indent_size 4`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_FOUR_SPACE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    ktlint = KtlintBlock(projectConfigPath = "config/custom.editorconfig"),
                ),
            extraRootFiles = mapOf("config/custom.editorconfig" to Configs.EDITORCONFIG_INDENT_4),
        )

        val result = projectDir.runTask("ktlintCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `custom projectConfig editorconfig is used - 2-space source fails under indent_size 4`() {
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
                    ktlint = KtlintBlock(projectConfigPath = "config/custom.editorconfig"),
                ),
            extraRootFiles = mapOf("config/custom.editorconfig" to Configs.EDITORCONFIG_INDENT_4),
        )

        val result = projectDir.runTaskWithFail("ktlintCheck")

        assertEquals(TaskOutcome.FAILED, result.task(":ktlintCheck")?.outcome)
        assertTrue(result.output.contains("standard:indent"), "expected an indent violation in output")
    }

    @Test
    fun `root editorconfig is used when no override is configured`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_FOUR_SPACE),
                    ),
                ),
            rootEditorConfigContent = Configs.EDITORCONFIG_INDENT_4,
        )

        val result = projectDir.runTask("ktlintCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `bundled default editorconfig (indent 2) is materialized and used when no file exists`() {
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

        val result = projectDir.runTask("ktlintCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `bundled default editorconfig rejects 4-space source`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_FOUR_SPACE),
                    ),
                ),
        )

        val result = projectDir.runTaskWithFail("ktlintCheck")

        assertEquals(TaskOutcome.FAILED, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `sources exclude excludes matching sources from check`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources =
                            mapOf(
                                "src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE,
                                "src/main/kotlin/ru/kode/legacy/Legacy.kt" to Sources.CLEAN_FOUR_SPACE,
                            ),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    ktlint = KtlintBlock(sources = SourcePatternsSlot(exclude = listOf("**/legacy/**"))),
                ),
        )

        val result = projectDir.runTask("ktlintCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `ktlintFormat fixes malformatted source so that ktlintCheck passes afterwards`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_FOUR_SPACE),
                    ),
                ),
        )

        val formatResult = projectDir.runTask("ktlintFormat")
        assertEquals(TaskOutcome.SUCCESS, formatResult.task(":ktlintFormat")?.outcome)

        val formatted = File(projectDir, "a/src/main/kotlin/ru/kode/test/Main.kt").readText()
        assertTrue(formatted.contains("\n  println"), "expected source reformatted to 2-space indent")

        val checkResult = projectDir.runTask("ktlintCheck")
        assertEquals(TaskOutcome.SUCCESS, checkResult.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `ktlintFormat is up-to-date on a second run with no source changes`() {
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

        val firstRun = projectDir.runTask("ktlintFormat")
        assertEquals(TaskOutcome.SUCCESS, firstRun.task(":ktlintFormat")?.outcome)

        val secondRun = projectDir.runTask("ktlintFormat")
        assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":ktlintFormat")?.outcome)
    }

    @Test
    fun `ktlintFormat reruns after the source it formatted changes again`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_FOUR_SPACE),
                    ),
                ),
        )

        val firstRun = projectDir.runTask("ktlintFormat")
        assertEquals(TaskOutcome.SUCCESS, firstRun.task(":ktlintFormat")?.outcome)

        // Write content that wasn't seen by the first run (not merely reverting to the
        // pre-format bytes) so the change is visible to ktlintFormat's input snapshot.
        val sourceFile = File(projectDir, "a/src/main/kotlin/ru/kode/test/Main.kt")
        sourceFile.writeText(Sources.CLEAN_FOUR_SPACE.replace("\"ok\"", "\"ok again\""))

        val secondRun = projectDir.runTask("ktlintFormat")
        assertEquals(TaskOutcome.SUCCESS, secondRun.task(":ktlintFormat")?.outcome)
    }

    @Test
    fun `sources include restricts check to only the matching sources`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources =
                            mapOf(
                                "src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE,
                                "src/main/kotlin/ru/kode/legacy/Legacy.kt" to Sources.CLEAN_FOUR_SPACE,
                            ),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    ktlint =
                        KtlintBlock(
                            sources = SourcePatternsSlot(useDefaults = false, include = listOf("**/test/**")),
                        ),
                ),
        )

        // Only src/main/kotlin/ru/kode/test/Main.kt matches the include pattern — the
        // malformatted Legacy.kt under ru/kode/legacy is never checked.
        val result = projectDir.runTask("ktlintCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `sources useDefaults false drops the bundled Kotlin globs`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_FOUR_SPACE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    ktlint = KtlintBlock(sources = SourcePatternsSlot(useDefaults = false)),
                ),
        )

        // useDefaults=false with no custom include leaves nothing matched — the malformatted
        // 4-space source is never checked, so the task succeeds despite the violation.
        val result = projectDir.runTask("ktlintCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `sources include exclude vararg sugar works the same as the list form`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources =
                            mapOf(
                                "src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE,
                                "src/main/kotlin/ru/kode/legacy/Legacy.kt" to Sources.CLEAN_FOUR_SPACE,
                            ),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    extraExtensionContent =
                        "ktlint.sources { useDefaults.set(false); include(\"**/test/**\", \"**/other/**\") }",
                ),
        )

        val result = projectDir.runTask("ktlintCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `ktlint cli useDefaults false without a replacement fails to resolve`() {
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
                    ktlint = KtlintBlock(cli = DependencySlot(useDefaults = false)),
                ),
        )

        // No default CLI dependency and nothing else wired -> the ktlint CLI classpath is
        // empty and the check task cannot run at all.
        val result = projectDir.runTaskWithFail("ktlintCheck")

        assertEquals(TaskOutcome.FAILED, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `missing ktlint-cli alias in version catalog still succeeds via the baked-in default`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            libsCatalog = LibsCatalog(includeKtlintCli = false),
        )

        // No `ktlint-cli` alias anywhere in the catalog — the plugin falls back to its own
        // baked-in coordinate instead of failing.
        val result = projectDir.runTask("ktlintCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintCheck")?.outcome)
    }
}
