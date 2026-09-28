package ru.kode.android.app.quality.plugin.foundation.engine

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskCollection
import ru.kode.android.app.quality.plugin.foundation.defaultToolVersion
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension
import ru.kode.android.app.quality.plugin.foundation.messages.detekt2PluginMissingMessage
import ru.kode.android.app.quality.plugin.foundation.messages.detekt2VersionMismatchMessage
import ru.kode.android.app.quality.plugin.foundation.messages.invalidDetektEngineMessage
import ru.kode.android.gradle.commons.logger.LoggerService
import java.io.File

const val DETEKT_ENGINE_PROPERTY = "ru.kode.appQuality.detektEngine"

private const val DETEKT2_PLUGIN_CLASS = "dev.detekt.gradle.plugin.DetektPlugin"

// Read reflectively: DETEKT_VERSION is a compile-time constant, a direct reference would inline
// the version this plugin was compiled against instead of the one on the consumer's classpath.
private const val DETEKT2_BUILD_CONFIG_CLASS = "dev.detekt.detekt_gradle_plugin.BuildConfig"

/** Extension values applied to each module's detekt extension, read once the root is evaluated. */
internal data class DetektSettings(
    val debug: Boolean,
    val ignoredBuildTypes: List<String>,
    val baseline: File?,
    val buildUponDefaultConfig: Boolean,
)

/**
 * Everything that differs between detekt 1 (`io.gitlab.arturbosch.detekt`) and detekt 2
 * (`dev.detekt`): the Gradle plugin and its task/extension types, the default rule artifacts
 * and the bundled kotlin config. Engine-agnostic wiring lives in `DetektWiring.kt`.
 */
internal interface DetektEngine {
    val pluginId: String
    val otherEnginePluginId: String
    val kotlinRulesAlias: String
    val androidRulesAlias: String
    val composeRulesAlias: String
    val kotlinConfigResource: String

    fun applyPlugin(project: Project)

    fun configureExtension(
        project: Project,
        configFile: Provider<RegularFile>,
        settings: () -> DetektSettings,
    )

    fun configureTasks(
        project: Project,
        extension: AppQualityFoundationExtension,
        loggerProvider: Provider<LoggerService>,
    )

    fun detektTasks(project: Project): TaskCollection<out Task>
}

/**
 * Picks the engine from the `ru.kode.appQuality.detektEngine` Gradle property (default `1`).
 * Engine 2 needs `dev.detekt` on the same classloader as this plugin — it is `compileOnly` here,
 * so the consumer must declare it next to the plugin (`id("dev.detekt") apply false`), and in
 * exactly the version this plugin is built against (detekt 2 is still in alpha).
 */
internal fun Project.resolveDetektEngine(): DetektEngine =
    when (val raw = providers.gradleProperty(DETEKT_ENGINE_PROPERTY).orNull?.trim()) {
        null, "1" -> Detekt1Engine
        "2" -> {
            val expected = detekt2Version()
            val loader = DetektEngine::class.java.classLoader
            try {
                Class.forName(DETEKT2_PLUGIN_CLASS, false, loader)
            } catch (_: ClassNotFoundException) {
                throw GradleException(detekt2PluginMissingMessage(expected))
            } catch (_: LinkageError) {
                throw GradleException(detekt2PluginMissingMessage(expected))
            }
            val actual =
                runCatching {
                    Class.forName(DETEKT2_BUILD_CONFIG_CLASS, false, loader).getField("DETEKT_VERSION").get(null)
                }.getOrNull() as? String
            if (actual != expected) throw GradleException(detekt2VersionMismatchMessage(expected, actual))
            Detekt2Engine
        }
        else -> throw GradleException(invalidDetektEngineMessage(raw))
    }

/** The `dev.detekt` version this plugin is built against (the ktlint wrapper shares it). */
internal fun detekt2Version(): String = defaultToolVersion("detekt-rules-ktlint-wrapper").substringAfterLast(':')
