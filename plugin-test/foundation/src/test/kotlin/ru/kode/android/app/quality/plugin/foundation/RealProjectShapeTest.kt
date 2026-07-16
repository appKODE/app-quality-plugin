package ru.kode.android.app.quality.plugin.foundation

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.kode.android.app.quality.plugin.test.utils.DependencySlot
import ru.kode.android.app.quality.plugin.test.utils.DetektBlock
import ru.kode.android.app.quality.plugin.test.utils.KtlintBlock
import ru.kode.android.app.quality.plugin.test.utils.LibsCatalog
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.PlatformDetektBlock
import ru.kode.android.app.quality.plugin.test.utils.QualityConfig
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.initGit
import ru.kode.android.app.quality.plugin.test.utils.runTask
import ru.kode.android.app.quality.plugin.test.utils.runTaskWithFail
import java.io.File

/**
 * Mirrors how the plugin is integrated in real KODE projects (loot & co): multi-module
 * android app + compose ui + pure-kotlin domain, extra build types, root .editorconfig,
 * `libs` catalog with all aliases, and the custom rules jar configured explicitly.
 */
class RealProjectShapeTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        projectDir = File(tempDir, "test-project")
    }

    private fun lootShapedModules() =
        listOf(
            ModuleSpec(
                name = "app",
                type = ModuleType.AndroidApp,
                buildTypes = listOf("internal", "demo"),
                kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
            ),
            ModuleSpec(
                name = "feature-ui",
                type = ModuleType.AndroidLib,
                applyComposePlugin = true,
                kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Ui.kt" to Sources.CLEAN_TWO_SPACE),
            ),
            ModuleSpec(
                name = "feature-domain",
                type = ModuleType.KotlinJvm,
                kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Domain.kt" to Sources.CLEAN_TWO_SPACE),
            ),
        )

    private fun lootShapedConfig() = QualityConfig(detekt = kodeRulesJarBlock())

    @Test
    fun `loot-shaped project passes pipelineCheck with compose layer end to end`() {
        val rulesJar = exampleRulesJar()
        assumeTrue(rulesJar != null, "example-project rules jar not found; skipping")

        projectDir.createQualityProject(
            modules = lootShapedModules(),
            qualityConfig = lootShapedConfig(),
            rootEditorConfigContent = Configs.EDITORCONFIG_INDENT_2,
            rulesJar = rulesJar,
        )
        projectDir.initGit()

        val result = projectDir.runTask("pipelineCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":pipelineCheck")?.outcome)
        // Every module's detekt ran: compose layer validated on feature-ui (the bundled
        // compose config's `compose:` rule set only loads with detekt-compose-rules resolved).
        listOf(":app", ":feature-ui", ":feature-domain").forEach { module ->
            assertTrue(
                result.tasks.map { it.path }.any { it.startsWith("$module:detekt") },
                "expected detekt tasks for $module",
            )
        }
    }

    @Test
    fun `loot-shaped project passes prePushCheck`() {
        val rulesJar = exampleRulesJar()
        assumeTrue(rulesJar != null, "example-project rules jar not found; skipping")

        projectDir.createQualityProject(
            modules = lootShapedModules(),
            qualityConfig = lootShapedConfig(),
            rootEditorConfigContent = Configs.EDITORCONFIG_INDENT_2,
            rulesJar = rulesJar,
        )
        projectDir.initGit()

        val result = projectDir.runTask("prePushCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":prePushCheck")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintFormat")?.outcome)
    }

    @Test
    fun `module-local compose config overrides the bundled compose default`() {
        val rulesJar = exampleRulesJar()
        assumeTrue(rulesJar != null, "example-project rules jar not found; skipping")

        // The module config activates a compose rule against which the source violates:
        // Modifier parameter not named `modifier` -> ComposeFunctionName? Use MaxLineLength
        // inside the compose config instead: any rule works, the assertion is that the
        // MODULE file (60-char limit) wins over the bundled default (no such limit).
        val strictComposeConfig =
            """
            |build:
            |  maxIssues: 0
            |
            |style:
            |  MaxLineLength:
            |    active: true
            |    maxLineLength: 60
            |
            """.trimMargin()

        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "feature-ui",
                        type = ModuleType.KotlinJvm,
                        applyComposePlugin = true,
                        detektComposeConfigContent = strictComposeConfig,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Long.kt" to Sources.LONG_LINE_80),
                    ),
                ),
            qualityConfig = lootShapedConfig(),
            rulesJar = rulesJar,
        )

        val result = projectDir.runTaskWithFail(":feature-ui:detekt")

        assertEquals(TaskOutcome.FAILED, result.task(":feature-ui:detekt")?.outcome)
        assertTrue(
            result.output.contains("MaxLineLength"),
            "expected the module compose config (60-char limit) to be applied",
        )
    }

    @Test
    fun `loot-shaped project works with typed accessors from a custom catalog`() {
        val rulesJar = exampleRulesJar()
        assumeTrue(rulesJar != null, "example-project rules jar not found; skipping")

        projectDir.createQualityProject(
            modules = lootShapedModules(),
            qualityConfig =
                QualityConfig(
                    ktlint =
                        KtlintBlock(
                            cli = DependencySlot(refs = listOf("deps.ktlint.cli"), useDefaults = false),
                        ),
                    detekt =
                        DetektBlock(
                            kotlin =
                                PlatformDetektBlock(
                                    rules =
                                        DependencySlot(
                                            refs = listOf("deps.detekt.formatting"),
                                            files = listOf("libs/detekt-rules-1.4.0.jar"),
                                            useDefaults = false,
                                        ),
                                ),
                            compose =
                                PlatformDetektBlock(
                                    rules =
                                        DependencySlot(
                                            refs = listOf("deps.detekt.compose.rules"),
                                            useDefaults = false,
                                        ),
                                ),
                        ),
                ),
            rootEditorConfigContent = Configs.EDITORCONFIG_INDENT_2,
            rulesJar = rulesJar,
            libsCatalog = LibsCatalog(name = "deps"),
        )
        projectDir.initGit()

        val result = projectDir.runTask("pipelineCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":pipelineCheck")?.outcome)
    }

    @Test
    fun `zero-config real production shape passes pipelineCheck with only verboseLogging set`() {
        // Mirrors what all 3 real adopters (ceb-mobile, esim-android, loot-android) actually do:
        // apply the plugin once at root and configure nothing but verboseLogging. No rules jar,
        // no editorconfig override, no custom sources/dependency slots — bundled defaults only.
        projectDir.createQualityProject(
            modules = lootShapedModules(),
            qualityConfig = QualityConfig(verboseLogging = false),
        )
        projectDir.initGit()

        val result = projectDir.runTask("pipelineCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":pipelineCheck")?.outcome)
    }
}
