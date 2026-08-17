package ru.kode.android.app.quality.plugin.foundation

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.QualityConfig
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.initGit
import ru.kode.android.app.quality.plugin.test.utils.runTask
import java.io.File

class GitHooksTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        projectDir = File(tempDir, "test-project")
    }

    private fun hooksPathFromGitConfig(): String? {
        return File(projectDir, ".git/config")
            .readLines()
            .firstOrNull { it.trim().startsWith("hooksPath") }
            ?.substringAfter("=")
            ?.trim()
    }

    @Test
    fun `custom gitHooks path is written to git config`() {
        projectDir.createQualityProject(
            modules = listOf(ModuleSpec(name = "a", type = ModuleType.KotlinJvm)),
            qualityConfig = QualityConfig(gitHooksPath = ".myhooks"),
            extraRootFiles = mapOf(".myhooks/.keep" to ""),
        )
        projectDir.initGit()

        val result = projectDir.runTask("gitHooksSetup")

        assertEquals(TaskOutcome.SUCCESS, result.task(":gitHooksSetup")?.outcome)
        val hooksPath = hooksPathFromGitConfig()
        assertTrue(
            hooksPath != null && hooksPath.endsWith(".myhooks"),
            "expected hooksPath ending with .myhooks, got: $hooksPath",
        )
    }

    @Test
    fun `default gitHooks path is rootProject githooks directory`() {
        projectDir.createQualityProject(
            modules = listOf(ModuleSpec(name = "a", type = ModuleType.KotlinJvm)),
        )
        projectDir.initGit()

        val result = projectDir.runTask("gitHooksSetup")

        assertEquals(TaskOutcome.SUCCESS, result.task(":gitHooksSetup")?.outcome)
        val hooksPath = hooksPathFromGitConfig()
        assertTrue(
            hooksPath != null && hooksPath.endsWith(".githooks"),
            "expected hooksPath ending with .githooks, got: $hooksPath",
        )
    }

    @Test
    fun `gitHooksSetup is skipped when project is not a git repo`() {
        projectDir.createQualityProject(
            modules = listOf(ModuleSpec(name = "a", type = ModuleType.KotlinJvm)),
        )
        // No initGit() call — project has no .git directory.

        val result = projectDir.runTask("gitHooksSetup")

        assertEquals(TaskOutcome.SKIPPED, result.task(":gitHooksSetup")?.outcome)
    }

    @Test
    fun `gitHooksSetup is skipped when gitHooksEnabled is false`() {
        projectDir.createQualityProject(
            modules = listOf(ModuleSpec(name = "a", type = ModuleType.KotlinJvm)),
            qualityConfig = QualityConfig(gitHooksEnabled = false),
        )
        projectDir.initGit()

        val result = projectDir.runTask("gitHooksSetup")

        assertEquals(TaskOutcome.SKIPPED, result.task(":gitHooksSetup")?.outcome)
    }

    @Test
    fun `prePushCheck succeeds without git hooks setup when gitHooksEnabled is false`() {
        projectDir.createQualityProject(
            modules = listOf(ModuleSpec(name = "a", type = ModuleType.KotlinJvm)),
            qualityConfig = QualityConfig(gitHooksEnabled = false),
        )
        // No initGit() call and no .githooks directory — would fail git config if not skipped.

        val result = projectDir.runTask("prePushCheck")

        assertEquals(TaskOutcome.SKIPPED, result.task(":gitHooksSetup")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":prePushCheck")?.outcome)
    }
}
