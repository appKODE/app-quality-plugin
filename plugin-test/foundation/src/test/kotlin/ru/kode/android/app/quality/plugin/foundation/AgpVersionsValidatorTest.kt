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
import ru.kode.android.app.quality.plugin.test.utils.getFile
import ru.kode.android.app.quality.plugin.test.utils.resolveRequiredAgpJars
import ru.kode.android.app.quality.plugin.test.utils.runTask
import ru.kode.android.app.quality.plugin.test.utils.runTaskWithFail
import ru.kode.android.app.quality.plugin.test.utils.runTasks
import java.io.File

/**
 * The plugin's documented usage applies it at the ROOT project, where AGP's
 * `AndroidComponentsExtension` never exists (it's only registered on modules that apply AGP
 * themselves) — so `stopExecutionIfNotSupported` never fires there. These tests instead exercise
 * the guard the only way it's reachable: applying the plugin directly to an Android module.
 */
class AgpVersionsValidatorTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        projectDir = File(tempDir, "test-project")
    }

    private fun writeSettings() {
        projectDir.getFile("settings.gradle").writeText(
            """
            pluginManagement {
                repositories {
                    mavenLocal()
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    mavenLocal()
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "agp-validator-test"
            """.trimIndent(),
        )
    }

    private fun writeManifest() {
        projectDir.getFile("src/main/AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/res/android" />
            """.trimIndent(),
        )
    }

    @Test
    fun `plugin applied directly to a library module fails with the actionable message`() {
        writeSettings()
        writeManifest()
        projectDir.getFile("build.gradle").writeText(
            """
            plugins {
                id 'com.android.library'
                id 'ru.kode.android.app-quality.foundation'
            }

            android {
                namespace = "ru.kode.test.agpvalidator"
                compileSdk 36
                defaultConfig {
                    minSdk 26
                }
            }
            """.trimIndent(),
        )

        // com.android.library registers AndroidComponentsExtension but has no AppPlugin —
        // the guard's second check (`!plugins.hasPlugin(AppPlugin::class.java)`) must fire.
        val result = projectDir.runTaskWithFail("help")

        assertTrue(
            result.output.contains("PLUGIN CONFIGURATION ERROR"),
            "expected the mustBeUsedWithAndroidMessage banner, got: ${result.output}",
        )
        assertTrue(
            result.output.contains("This plugin can only be used with Android application"),
            "expected the specific guidance text",
        )
    }

    @Test
    fun `plugin applied with an AGP version below the minimum fails with the version message`() {
        writeSettings()
        writeManifest()
        projectDir.getFile("build.gradle").writeText(
            """
            plugins {
                id 'com.android.application'
                id 'ru.kode.android.app-quality.foundation'
            }

            android {
                namespace = "ru.kode.test.agpvalidator"
                compileSdk 33
                defaultConfig {
                    applicationId "ru.kode.test.agpvalidator"
                    minSdk 26
                    targetSdk 33
                    versionCode 1
                    versionName "1.0"
                }
            }
            """.trimIndent(),
        )

        // AgpVersions.MIN_VERSION is 7.4.0 — 7.3.1 has AppPlugin but must fail the version
        // check first (it runs before the AppPlugin check in stopExecutionIfNotSupported).
        val result =
            projectDir.runTasks(
                "help",
                agpClasspath = resolveRequiredAgpJars(BELOW_MIN_AGP_VERSION),
                gradleVersion = BELOW_MIN_AGP_GRADLE_VERSION,
                expectFailure = true,
            )

        assertTrue(
            result.output.contains("UNSUPPORTED ANDROID GRADLE PLUGIN VERSION"),
            "expected the mustBeUsedWithVersionMessage banner, got: ${result.output}",
        )
    }

    @Test
    fun `plugin applied directly to an app module with a supported AGP version does not fail`() {
        writeSettings()
        writeManifest()
        projectDir.getFile("build.gradle").writeText(
            """
            plugins {
                id 'com.android.application'
                id 'ru.kode.android.app-quality.foundation'
            }

            android {
                namespace = "ru.kode.test.agpvalidator"
                compileSdk 36
                defaultConfig {
                    applicationId "ru.kode.test.agpvalidator"
                    minSdk 26
                    targetSdk 36
                    versionCode 1
                    versionName "1.0"
                }
            }
            """.trimIndent(),
        )

        // A supported AGP version + com.android.application (AppPlugin present) — the guard's
        // both checks pass and stopExecutionIfNotSupported must not throw.
        val result = projectDir.runTask("help")

        assertEquals(TaskOutcome.SUCCESS, result.task(":help")?.outcome)
        assertTrue(
            !result.output.contains("PLUGIN CONFIGURATION ERROR"),
            "did not expect the mustBeUsedWithAndroidMessage banner, got: ${result.output}",
        )
        assertTrue(
            !result.output.contains("UNSUPPORTED ANDROID GRADLE PLUGIN VERSION"),
            "did not expect the mustBeUsedWithVersionMessage banner, got: ${result.output}",
        )
    }

    @Test
    fun `subproject applying AGP directly with an unsupported version fails validation`() {
        // Plugin applied only at the root (its documented usage) — the subproject applies
        // com.android.library on its own, which is the path validateSubprojectAgpVersion covers.
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(name = "a", type = ModuleType.AndroidLib),
                ),
        )

        val result =
            projectDir.runTasks(
                "help",
                agpClasspath = resolveRequiredAgpJars(BELOW_MIN_AGP_VERSION),
                gradleVersion = BELOW_MIN_AGP_GRADLE_VERSION_MULTI_MODULE,
                expectFailure = true,
            )

        assertTrue(
            result.output.contains("UNSUPPORTED ANDROID GRADLE PLUGIN VERSION"),
            "expected the mustBeUsedWithVersionMessage banner from the subproject, got: ${result.output}",
        )
    }

    @Test
    fun `subproject applying AGP directly with a supported version does not fail`() {
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(name = "a", type = ModuleType.AndroidLib),
                ),
        )

        val result = projectDir.runTask("help")

        assertEquals(TaskOutcome.SUCCESS, result.task(":help")?.outcome)
        assertTrue(
            !result.output.contains("UNSUPPORTED ANDROID GRADLE PLUGIN VERSION"),
            "did not expect the mustBeUsedWithVersionMessage banner, got: ${result.output}",
        )
    }

    private companion object {
        const val BELOW_MIN_AGP_VERSION = "7.3.1"
        const val BELOW_MIN_AGP_GRADLE_VERSION = "8.6"
        const val BELOW_MIN_AGP_GRADLE_VERSION_MULTI_MODULE = "8.7"
    }
}
