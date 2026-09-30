package ru.kode.android.app.quality.plugin.foundation.engine

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskCollection
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool
import ru.kode.android.app.quality.plugin.foundation.ANDROID_BASE_PLUGIN_ID
import ru.kode.android.app.quality.plugin.foundation.KOTLIN_MULTIPLATFORM_PLUGIN_ID
import ru.kode.android.app.quality.plugin.foundation.configureDetektSources
import ru.kode.android.app.quality.plugin.foundation.excludeGeneratedSources
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension
import ru.kode.android.app.quality.plugin.foundation.messages.typeResolutionComponentsMessage
import ru.kode.android.app.quality.plugin.foundation.messages.typeResolutionNothingToAnalyseMessage
import ru.kode.android.app.quality.plugin.foundation.recordVariants
import ru.kode.android.app.quality.plugin.foundation.typeResolutionComponents
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
            excludeGeneratedSources(task)
            task.reports {
                it.xml.required.set(detektConfig.xmlReportEnabled)
                it.html.required.set(false)
                it.txt.required.set(false)
                it.sarif.required.set(detektConfig.sarifReportEnabled)
            }
        }
        // Detekt's own type-resolved tasks keep their compilation's sources and jvmTarget.
        project.tasks.named("detekt", Detekt::class.java).configure { task ->
            task.jvmTarget = extension.jvmTarget.get().target
            project.configureDetektSources(task, detektConfig)
        }
        // Detekt 1.23 has no type-resolved tasks on AGP 9 built-in Kotlin, so the plain task
        // gets the classpath of the components analysed with type resolution. Delete with engine 1.
        project.pluginManager.withPlugin(ANDROID_BASE_PLUGIN_ID) {
            val variants = mutableMapOf<String, String>()
            project.recordVariants(variants)
            project.tasks.named("detekt", Detekt::class.java).configure { task ->
                if (!detektConfig.typeResolution.get() || !project.hasBuiltInKotlin()) return@configure
                val ignoredBuildTypes = detektConfig.ignoredBuildTypes.get()
                val ignoredComponents = detektConfig.ignoredTypeResolutionVariants.get()
                val kotlin = project.extensions.findByType(KotlinAndroidExtension::class.java)
                val components = typeResolutionComponents(variants, ignoredBuildTypes, ignoredComponents)
                // Without an androidTest classpath its sources would be analysed half-resolved.
                if (components.none { it.endsWith("AndroidTest") }) {
                    task.exclude("**/src/androidTest/**", "**/src/androidTest*/**")
                }
                val compilations = components.mapNotNull { kotlin?.target?.compilations?.findByName(it) }
                val selected = compilations.map { it.name }
                val skipped = typeResolutionComponents(variants, emptyList(), emptyList()) - selected.toSet()
                project.logger.info(typeResolutionComponentsMessage(project.path, selected, skipped))
                if (compilations.isEmpty()) {
                    // Fail on run, not on configuration, so `./gradlew tasks` still works.
                    val message =
                        typeResolutionNothingToAnalyseMessage(
                            project.path,
                            variants.toMap(),
                            ignoredBuildTypes,
                            ignoredComponents,
                            components,
                        )
                    task.doFirst { throw GradleException(message) }
                    return@configure
                }
                compilations.forEach { compilation ->
                    task.classpath.from(
                        compilation.output.classesDirs,
                        compilation.compileTaskProvider.map { (it as KotlinCompileTool).libraries },
                    )
                }
            }
        }
    }

    override fun detektTasks(project: Project): TaskCollection<out Task> = project.tasks.withType(Detekt::class.java)
}

/** An Android module compiled by AGP 9 built-in Kotlin: detekt 1 registers no variant tasks for it. */
internal fun Project.hasBuiltInKotlin(): Boolean =
    plugins.hasPlugin(ANDROID_BASE_PLUGIN_ID) &&
        !plugins.hasPlugin("org.jetbrains.kotlin.android") &&
        !plugins.hasPlugin(KOTLIN_MULTIPLATFORM_PLUGIN_ID) &&
        hasKotlin()

/** The module compiles Kotlin: KGP and AGP 9 built-in Kotlin both register the `kotlin` extension. */
internal fun Project.hasKotlin(): Boolean = extensions.findByName("kotlin") != null

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
