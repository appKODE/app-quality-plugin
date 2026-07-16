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
import ru.kode.android.app.quality.plugin.test.utils.resolveRequiredAgpJars
import ru.kode.android.app.quality.plugin.test.utils.runTask
import ru.kode.android.app.quality.plugin.test.utils.runTasks
import java.io.File

class AggregateTasksTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        projectDir = File(tempDir, "test-project")
    }

    private fun kotlinModule(name: String) =
        ModuleSpec(
            name = name,
            type = ModuleType.KotlinJvm,
            kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
        )

    @Test
    fun `pipelineCheck runs subproject detekt tasks`() {
        projectDir.createQualityProject(modules = listOf(kotlinModule("a")))
        projectDir.initGit()

        val result = projectDir.runTask("pipelineCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":pipelineCheck")?.outcome)
        val detektTasks = result.tasks.map { it.path }.filter { it.startsWith(":a:detekt") }
        assertTrue(detektTasks.isNotEmpty(), "pipelineCheck must trigger detekt tasks of subprojects")
    }

    @Test
    fun `pipelineCheck on android module runs variant detekt tasks except ignored build types`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "app",
                        type = ModuleType.AndroidApp,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig = QualityConfig(detekt = kodeRulesJarBlock()),
            rulesJar = exampleRulesJar(),
        )
        projectDir.initGit()

        val result = projectDir.runTask("pipelineCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":pipelineCheck")?.outcome)
        val detektTasks = result.tasks.map { it.path }.filter { it.startsWith(":app:detekt") }
        assertTrue(detektTasks.isNotEmpty(), "pipelineCheck must trigger detekt tasks of the app module")
        assertTrue(
            detektTasks.none { it.contains("release", ignoreCase = true) },
            "release detekt tasks must be excluded by default ignoredBuildTypes, got: $detektTasks",
        )
    }

    // Variant detekt tasks (detektDebug/detektRelease) only exist with the classic
    // org.jetbrains.kotlin.android setup: detekt 1.x does not support AGP 9 built-in Kotlin
    // (https://github.com/detekt/detekt/issues/8320 — fixed only in detekt 2.0.0-alpha.3+,
    // which moved to dev.detekt coordinates). So this scenario runs on an injected AGP 8.x
    // with an older Gradle, the same trick build-publish uses for AGP/Gradle matrices.
    @Test
    fun `custom ignoredBuildTypes excludes matching variant detekt tasks with legacy AGP`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "app",
                        type = ModuleType.AndroidApp,
                        buildTypes = listOf("internal", "demo"),
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                        compileSdk = 35,
                        applyKotlinAndroidPlugin = true,
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt = kodeRulesJarBlock().copy(ignoredBuildTypes = listOf("debug")),
                ),
            rulesJar = exampleRulesJar(),
        )
        projectDir.initGit()

        val result =
            projectDir.runTasks(
                "pipelineCheck",
                agpClasspath = resolveRequiredAgpJars(LEGACY_AGP_VERSION),
                gradleVersion = LEGACY_GRADLE_VERSION,
            )

        assertEquals(TaskOutcome.SUCCESS, result.task(":pipelineCheck")?.outcome)
        val detektTasks = result.tasks.map { it.path }.filter { it.startsWith(":app:detekt") }
        assertTrue(
            detektTasks.any { it != ":app:detekt" },
            "expected variant detekt tasks with the kotlin-android plugin, got: $detektTasks",
        )
        assertTrue(
            detektTasks.none { it.contains("debug", ignoreCase = true) },
            "debug detekt tasks must be excluded by custom ignoredBuildTypes, got: $detektTasks",
        )
        // Custom ignoredBuildTypes REPLACES the default list ([release, internal, external,
        // demo]) — build types no longer ignored get variant tasks again.
        listOf("release", "internal", "demo").forEach { buildType ->
            assertTrue(
                detektTasks.any { it.contains(buildType, ignoreCase = true) },
                "$buildType detekt tasks must run when only debug is ignored, got: $detektTasks",
            )
        }
    }

    @Test
    fun `prePushCheck runs ktlintFormat instead of ktlintCheck`() {
        projectDir.createQualityProject(modules = listOf(kotlinModule("a")))
        projectDir.initGit()

        val result = projectDir.runTask("prePushCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":prePushCheck")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintFormat")?.outcome)
        assertEquals(null, result.task(":ktlintCheck"), "prePushCheck must not run ktlintCheck")
    }

    @Test
    fun `printRequiredGradleJvmargs prints the running JVM's input arguments`() {
        projectDir.createQualityProject(modules = listOf(kotlinModule("a")))
        projectDir.initGit()

        val result = projectDir.runTask("printRequiredGradleJvmargs")

        assertEquals(TaskOutcome.SUCCESS, result.task(":printRequiredGradleJvmargs")?.outcome)
        assertTrue(result.output.contains("Args: "), "expected the 'Args: ' line, got: ${result.output}")
    }

    @Test
    fun `androidLint is off by default and does not wire the lint task into pipelineCheck`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "app",
                        type = ModuleType.AndroidApp,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig = QualityConfig(detekt = kodeRulesJarBlock()),
            rulesJar = exampleRulesJar(),
        )
        projectDir.initGit()

        val result = projectDir.runTask("pipelineCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":pipelineCheck")?.outcome)
        assertTrue(
            result.tasks.none { it.path.startsWith(":app:lint") },
            "pipelineCheck must not trigger AGP lint tasks when androidLint is left at its default",
        )
    }

    @Test
    fun `androidLint enabled wires the lint task into pipelineCheck and prePushCheck`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "app",
                        type = ModuleType.AndroidApp,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt = kodeRulesJarBlock(),
                    extraExtensionContent = "androidLint.enabled.set(true)",
                ),
            rulesJar = exampleRulesJar(),
        )
        projectDir.initGit()

        val result = projectDir.runTask("pipelineCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":pipelineCheck")?.outcome)
        assertTrue(
            result.tasks.any { it.path.startsWith(":app:lint") },
            "pipelineCheck must trigger AGP lint tasks once androidLint.enabled is set, got: ${result.tasks.map { it.path }}",
        )
    }

    @Test
    fun `aggregate tasks are listed in the verification group`() {
        projectDir.createQualityProject(modules = listOf(kotlinModule("a")))
        projectDir.initGit()

        val result = projectDir.runTask("tasks")

        val verificationSection =
            result.output
                .substringAfter("Verification tasks", missingDelimiterValue = "")
                .substringBefore("\n\n")
        assertTrue(
            verificationSection.contains("pipelineCheck"),
            "pipelineCheck must be listed under Verification tasks",
        )
        assertTrue(
            verificationSection.contains("prePushCheck"),
            "prePushCheck must be listed under Verification tasks",
        )
    }
}
