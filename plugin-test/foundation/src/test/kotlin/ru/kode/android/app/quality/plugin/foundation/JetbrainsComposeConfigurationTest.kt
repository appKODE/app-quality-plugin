package ru.kode.android.app.quality.plugin.foundation

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.kode.android.app.quality.plugin.test.utils.DependencySlot
import ru.kode.android.app.quality.plugin.test.utils.DetektBlock
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.PlatformDetektBlock
import ru.kode.android.app.quality.plugin.test.utils.QualityConfig
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.runTask
import java.io.File

/**
 * `org.jetbrains.compose` (JetBrains Compose Multiplatform) is one of the two COMPOSE-platform
 * trigger plugin IDs the plugin recognizes (the other is `org.jetbrains.kotlin.plugin.compose`,
 * exercised by the applyComposePlugin=true tests elsewhere). This verifies it configures the
 * exact same compose detekt layer as its sibling.
 */
class JetbrainsComposeConfigurationTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        projectDir = File(tempDir, "test-project")
    }

    @Test
    fun `org-jetbrains-compose triggers the compose detekt layer via the baked-in default`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        applyJetbrainsComposePlugin = true,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
        )

        // No detekt.compose.rules configured — the compose config's `compose:` rule set only
        // validates if the plugin's bundled default wires detekt-compose-rules automatically.
        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `org-jetbrains-compose module accepts an explicit compose rules coordinate`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        applyJetbrainsComposePlugin = true,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt =
                        DetektBlock(
                            compose =
                                PlatformDetektBlock(
                                    rules =
                                        DependencySlot(
                                            notations = listOf("ru.kode:detekt-rules-compose:1.4.0"),
                                            useDefaults = false,
                                        ),
                                ),
                        ),
                ),
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }
}
