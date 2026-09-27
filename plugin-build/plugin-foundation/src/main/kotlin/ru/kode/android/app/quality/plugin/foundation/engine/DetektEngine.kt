package ru.kode.android.app.quality.plugin.foundation.engine

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskCollection
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension
import ru.kode.android.app.quality.plugin.foundation.messages.detekt2PluginMissingMessage
import ru.kode.android.app.quality.plugin.foundation.messages.invalidDetektEngineMessage
import ru.kode.android.gradle.commons.logger.LoggerService
import java.io.File

const val DETEKT_ENGINE_PROPERTY = "ru.kode.appQuality.detektEngine"

private const val DETEKT2_PLUGIN_CLASS = "dev.detekt.gradle.plugin.DetektPlugin"

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
 * so the consumer must declare it next to the plugin (`id("dev.detekt") apply false`).
 */
internal fun Project.resolveDetektEngine(): DetektEngine =
    when (val raw = providers.gradleProperty(DETEKT_ENGINE_PROPERTY).orNull?.trim()) {
        null, "1" -> Detekt1Engine
        "2" -> {
            try {
                Class.forName(DETEKT2_PLUGIN_CLASS, false, DetektEngine::class.java.classLoader)
            } catch (_: ClassNotFoundException) {
                throw GradleException(detekt2PluginMissingMessage())
            }
            Detekt2Engine
        }
        else -> throw GradleException(invalidDetektEngineMessage(raw))
    }
