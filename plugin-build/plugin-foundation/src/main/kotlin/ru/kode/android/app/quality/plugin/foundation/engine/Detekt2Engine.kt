package ru.kode.android.app.quality.plugin.foundation.engine

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.plugin.DetektPlugin
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskCollection
import ru.kode.android.app.quality.plugin.foundation.configureDetektSources
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension
import ru.kode.android.app.quality.plugin.foundation.messages.detekt2KotlinPluginTooOldMessage
import ru.kode.android.app.quality.plugin.foundation.excludeGeneratedSources
import ru.kode.android.gradle.commons.logger.LoggerService

/**
 * detekt 2 (`dev.detekt`). Only loaded once [resolveDetektEngine] has confirmed the consumer put
 * `dev.detekt` on the plugin classpath — this plugin depends on it `compileOnly`.
 */
internal object Detekt2Engine : DetektEngine {
    override val pluginId = "dev.detekt"
    override val otherEnginePluginId = "io.gitlab.arturbosch.detekt"
    override val kotlinRulesAlias = "detekt-rules-ktlint-wrapper"
    override val androidRulesAlias = "detekt-rules-detekt2"
    override val composeRulesAlias = "detekt-rules-compose-detekt2"
    override val kotlinConfigResource = "detekt/default.kotlin-config-detekt2.yml"

    override fun applyPlugin(project: Project) {
        // detekt 2 needs KGP 2.1.21+ (KotlinJvmExtension); older KGP fails with a raw NoClassDefFoundError.
        val loader = DetektPlugin::class.java.classLoader
        if (loader.hasClass("org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension") &&
            !loader.hasClass("org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension")
        ) {
            throw GradleException(detekt2KotlinPluginTooOldMessage(project.path))
        }
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
                detektExtension.debug.set(values.debug)
                detektExtension.ignoredBuildTypes.set(
                    (detektExtension.ignoredBuildTypes.get() + values.ignoredBuildTypes).distinct(),
                )
                detektExtension.buildUponDefaultConfig.set(values.buildUponDefaultConfig)
                values.baseline?.let { detektExtension.baseline.set(it) }
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
            task.jvmTarget.set(extension.jvmTarget.map { it.target })
            task.debug.set(extension.verboseLogging)
            task.config.from(detektConfig.additionalConfigs)
        }
        project.tasks.withType(Detekt::class.java).configureEach { task ->
            task.usesService(loggerProvider)
            task.debug.set(extension.verboseLogging)
            excludeGeneratedSources(task)
            // After detekt seeds the task config from the extension, so these merge last.
            task.config.from(detektConfig.additionalConfigs)
            task.reports {
                // detekt 2 has no xml report; checkstyle is the same format under its real name.
                it.checkstyle.required.set(detektConfig.xmlReportEnabled)
                it.html.required.set(false)
                it.markdown.required.set(false)
                it.sarif.required.set(detektConfig.sarifReportEnabled)
            }
        }
        // Detekt's own type-resolved tasks keep their compilation's sources and jvmTarget.
        project.tasks.named("detekt", Detekt::class.java).configure { task ->
            task.jvmTarget.set(extension.jvmTarget.map { it.target })
            project.configureDetektSources(task, detektConfig)
        }
    }

    override fun detektTasks(project: Project): TaskCollection<out Task> = project.tasks.withType(Detekt::class.java)
}

private fun ClassLoader.hasClass(name: String): Boolean =
    try {
        Class.forName(name, false, this)
        true
    } catch (_: ClassNotFoundException) {
        false
    }
