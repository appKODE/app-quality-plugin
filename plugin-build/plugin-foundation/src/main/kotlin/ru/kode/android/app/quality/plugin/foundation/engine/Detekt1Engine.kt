package ru.kode.android.app.quality.plugin.foundation.engine

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskCollection
import ru.kode.android.app.quality.plugin.foundation.configureDetektSources
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension
import ru.kode.android.app.quality.plugin.foundation.typeResolutionLibraries
import ru.kode.android.gradle.commons.logger.LoggerService

/** detekt 1.23 (`io.gitlab.arturbosch.detekt`), bundled with the plugin. */
internal object Detekt1Engine : DetektEngine {
    override val pluginId = "io.gitlab.arturbosch.detekt"
    override val otherEnginePluginId = "dev.detekt"
    override val kotlinRulesAlias = "detekt-formatting"
    override val androidRulesAlias = "detekt-rules"
    override val composeRulesAlias = "detekt-compose-rules"
    override val kotlinConfigResource = "detekt/default.kotlin-config.yml"

    override fun applyPlugin(project: Project) {
        project.pluginManager.apply(DetektPlugin::class.java)
    }

    override fun configureExtension(
        project: Project,
        configFile: Provider<RegularFile>,
        settings: () -> DetektSettings,
    ) {
        project.extensions.configure(DetektExtension::class.java) { detektExtension ->
            detektExtension.config.from(configFile)
            project.afterRootEvaluated {
                val values = settings()
                detektExtension.debug = values.debug
                detektExtension.ignoredBuildTypes =
                    (detektExtension.ignoredBuildTypes + values.ignoredBuildTypes).distinct()
                detektExtension.buildUponDefaultConfig = values.buildUponDefaultConfig
                values.baseline?.let { detektExtension.baseline = it }
            }
        }
    }

    override fun configureTasks(
        project: Project,
        extension: AppQualityFoundationExtension,
        loggerProvider: Provider<LoggerService>,
    ) {
        val detektConfig = extension.detekt
        project.tasks.withType(DetektCreateBaselineTask::class.java).configureEach { task ->
            task.usesService(loggerProvider)
            task.jvmTarget = extension.jvmTarget.get().target
            task.debug.set(extension.verboseLogging)
        }
        project.tasks.withType(Detekt::class.java).configureEach { task ->
            task.usesService(loggerProvider)
            task.debug = extension.verboseLogging.get()
            task.jvmTarget = extension.jvmTarget.get().target
            project.typeResolutionLibraries(task.name, detektConfig)?.let { task.classpath.setFrom(it) }
            project.configureDetektSources(task, detektConfig)
            task.reports {
                it.xml.required.set(detektConfig.xmlReportEnabled)
                it.html.required.set(false)
                it.txt.required.set(false)
                it.sarif.required.set(detektConfig.sarifReportEnabled)
            }
        }
    }

    override fun detektTasks(project: Project): TaskCollection<out Task> = project.tasks.withType(Detekt::class.java)
}

/**
 * `extension` lives on the root project, and detekt is configured while a SUBPROJECT's plugins
 * are being applied — reading the extension's values right away would depend on the root build
 * script having already run its `appQualityFoundation { }` block, which Gradle does not guarantee.
 * Run immediately if the root is already evaluated (the common case); otherwise defer to its
 * `afterEvaluate`, which Gradle forbids registering once the root has finished evaluating.
 */
internal fun Project.afterRootEvaluated(action: () -> Unit) {
    if (rootProject.state.executed) {
        action()
    } else {
        rootProject.afterEvaluate { action() }
    }
}
