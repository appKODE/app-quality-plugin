@file:Suppress("MatchingDeclarationName") // file groups the aggregate-task wiring fun with its small result type

package ru.kode.android.app.quality.plugin.foundation

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import ru.kode.android.app.quality.plugin.foundation.engine.Detekt1Engine
import ru.kode.android.app.quality.plugin.foundation.engine.DetektEngine
import ru.kode.android.app.quality.plugin.foundation.engine.hasBuiltInKotlin
import ru.kode.android.app.quality.plugin.foundation.engine.hasKotlin
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension
import ru.kode.android.app.quality.plugin.foundation.messages.typeResolutionComponentsMessage
import ru.kode.android.app.quality.plugin.foundation.messages.typeResolutionMultiplatformMessage
import ru.kode.android.app.quality.plugin.foundation.messages.typeResolutionNothingToAnalyseMessage
import ru.kode.android.app.quality.plugin.foundation.task.GitHooksSetupTask
import ru.kode.android.gradle.commons.logger.LoggerService
import java.lang.management.ManagementFactory

internal fun Project.configureGitHooksSetup(extension: AppQualityFoundationExtension): TaskProvider<GitHooksSetupTask> =
    tasks.register("gitHooksSetup", GitHooksSetupTask::class.java) { task ->
        task.hooksPath.set(extension.gitHooks.map { it.asFile.path })
        task.rootDir.set(rootProject.layout.projectDirectory)
        // Capture only the Provider, not `extension` itself — the extension also holds
        // dependency-slot config (FileCollection/Dependency) that can't be configuration-cache
        // serialized, and onlyIf closures are stored as part of the cached task graph.
        val enabled = extension.gitHooksEnabled
        task.onlyIf("git hooks setup is enabled") { enabled.get() }
    }

internal fun configurePrintRequiredGradleJvmargs(project: Project) {
    project.tasks.register("printRequiredGradleJvmargs") { task ->
        task.doLast {
            val args =
                ManagementFactory.getRuntimeMXBean()
                    .inputArguments
                    .joinToString(" ")
            // Need to print into console each time, no need to use logger
            println("Args: $args")
        }
    }
}

/**
 * Wires the aggregate tasks to subproject detekt tasks through a provider: the dependencies
 * resolve at task-graph time, after all subprojects evaluate, so late-registered variant tasks
 * are seen and nothing is realized eagerly.
 */
internal fun Project.configureAggregateTasks(
    extension: AppQualityFoundationExtension,
    gitHooksSetup: TaskProvider<GitHooksSetupTask>,
    ktlintTasks: KtlintTasks,
    loggerProvider: Provider<LoggerService>,
    engine: DetektEngine,
): AggregateTasks {
    val ignoredBuildTypes = extension.detekt.ignoredBuildTypes
    val ignoredComponents = extension.detekt.ignoredTypeResolutionVariants
    val typeResolution = extension.detekt.typeResolution

    val pipelineCheck =
        tasks.register("pipelineCheck") { task ->
            task.usesService(loggerProvider)
            task.group = "verification"
            task.description = "Runs git hooks setup, ktlint check and detekt on all modules"
            task.dependsOn(gitHooksSetup, ktlintTasks.check)
        }
    val prePushCheck =
        tasks.register("prePushCheck") { task ->
            task.usesService(loggerProvider)
            task.group = "verification"
            task.description = "Runs git hooks setup, ktlint format and detekt on all modules"
            task.dependsOn(gitHooksSetup, ktlintTasks.format)
        }

    subprojects { subproject ->
        val variants = mutableMapOf<String, String>()
        subproject.pluginManager.withPlugin(ANDROID_BASE_PLUGIN_ID) { subproject.recordVariants(variants) }
        val detektTasks =
            subproject.provider {
                subproject.selectedDetektTasks(
                    typeResolution.get(),
                    engine,
                    variants,
                    ignoredBuildTypes.get(),
                    ignoredComponents.get(),
                )
            }
        pipelineCheck.configure { it.dependsOn(detektTasks) }
        prePushCheck.configure { it.dependsOn(detektTasks) }
        engine.detektTasks(subproject).configureEach { task ->
            task.mustRunAfter(gitHooksSetup, ktlintTasks.check, ktlintTasks.format)
        }
    }

    return AggregateTasks(pipelineCheck, prePushCheck)
}

/**
 * The detekt tasks the aggregate tasks run for this module: the plain `detekt` task with type
 * resolution off, on KMP (with a warning) and on Java-only Android modules. With type resolution
 * on, JVM modules run detekt's `detektMain`/`detektTest`; Android modules run `detekt<Component>`
 * for each of [typeResolutionComponents] (not detekt's `detektMain`/`detektTest`, which cover
 * every variant outside detekt's exact-match ignoredBuildTypes, androidTest included), or the
 * plain task where detekt registers none (detekt 1 on AGP 9 built-in Kotlin, which
 * [Detekt1Engine] feeds those components' classpath). A module without detekt contributes none;
 * an Android module with nothing left to analyse fails the build instead of analysing nothing.
 */
private fun Project.selectedDetektTasks(
    typeResolution: Boolean,
    engine: DetektEngine,
    variants: Map<String, String>,
    ignoredBuildTypes: List<String>,
    ignoredComponents: List<String>,
): List<TaskProvider<Task>> {
    if ("detekt" !in tasks.names) return emptyList()
    val names =
        when {
            !typeResolution -> listOf("detekt")
            plugins.hasPlugin(KOTLIN_MULTIPLATFORM_PLUGIN_ID) -> {
                logger.warn(typeResolutionMultiplatformMessage(path))
                listOf("detekt")
            }
            !hasKotlin() -> listOf("detekt") // module without Kotlin: nothing to type-resolve
            plugins.hasPlugin(ANDROID_BASE_PLUGIN_ID) && !(engine is Detekt1Engine && hasBuiltInKotlin()) -> {
                val components = typeResolutionComponents(variants, ignoredBuildTypes, ignoredComponents)
                val (selected, unregistered) =
                    components.partition { "detekt" + it.replaceFirstChar(Char::uppercase) in tasks.names }
                val skipped = typeResolutionComponents(variants, emptyList(), emptyList()) - selected.toSet()
                logger.info(typeResolutionComponentsMessage(path, selected, skipped))
                selected
                    .map { "detekt" + it.replaceFirstChar(Char::uppercase) }
                    .ifEmpty {
                        throw GradleException(
                            typeResolutionNothingToAnalyseMessage(
                                path,
                                variants,
                                ignoredBuildTypes,
                                ignoredComponents,
                                unregistered,
                            ),
                        )
                    }
            }
            else -> listOf("detektMain", "detektTest").filter { it in tasks.names }.ifEmpty { listOf("detekt") }
        }
    return names.map { tasks.named(it) }
}

/**
 * The Android components analysed with type resolution: each of [variants] (name to build type)
 * with its unit and android tests, minus those whose variant name (and so build type) contains
 * an [ignoredBuildTypes] entry and those whose own name contains an [ignoredComponents] entry
 * (substrings, ignoring case, as AQP matches ignoredBuildTypes elsewhere).
 */
internal fun typeResolutionComponents(
    variants: Map<String, String>,
    ignoredBuildTypes: List<String>,
    ignoredComponents: List<String>,
): List<String> =
    variants.keys
        .sorted()
        // A variant's name contains its build type, so matching the name covers both.
        .filter { name -> ignoredBuildTypes.none { name.contains(it, true) } }
        .flatMap { listOf(it, "${it}UnitTest", "${it}AndroidTest") }
        .filter { component -> ignoredComponents.none { component.contains(it, true) } }

/** Records each AGP variant's name and build type into [variants] as AGP creates them. */
internal fun Project.recordVariants(variants: MutableMap<String, String>) {
    extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
        variants[variant.name] = variant.buildType.orEmpty()
    }
}

internal const val KOTLIN_MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
internal const val ANDROID_BASE_PLUGIN_ID = "com.android.base"

internal data class AggregateTasks(
    val pipelineCheck: TaskProvider<Task>,
    val prePushCheck: TaskProvider<Task>,
)
