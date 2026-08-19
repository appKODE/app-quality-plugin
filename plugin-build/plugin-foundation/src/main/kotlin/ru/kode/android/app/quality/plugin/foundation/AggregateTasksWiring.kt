@file:Suppress("MatchingDeclarationName") // file groups the aggregate-task wiring fun with its small result type

package ru.kode.android.app.quality.plugin.foundation

import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension
import ru.kode.android.app.quality.plugin.foundation.task.GitHooksSetupTask
import ru.kode.android.gradle.commons.logger.LoggerService
import java.lang.management.ManagementFactory

internal fun Project.configureGitHooksSetup(extension: AppQualityFoundationExtension): TaskProvider<GitHooksSetupTask> {
    return tasks.register("gitHooksSetup", GitHooksSetupTask::class.java) { task ->
        task.hooksPath.set(extension.gitHooks.map { it.asFile.path })
        task.rootDir.set(rootProject.layout.projectDirectory)
        // Capture only the Provider, not `extension` itself — the extension also holds
        // dependency-slot config (FileCollection/Dependency) that can't be configuration-cache
        // serialized, and onlyIf closures are stored as part of the cached task graph.
        val enabled = extension.gitHooksEnabled
        task.onlyIf("git hooks setup is enabled") { enabled.get() }
    }
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
 * Wires the aggregate tasks to subproject detekt tasks through live, lazily filtered task
 * collections: the dependencies resolve at task-graph time, after all subprojects evaluate,
 * so late-registered variant tasks are included and nothing is realized eagerly.
 */
internal fun Project.configureAggregateTasks(
    extension: AppQualityFoundationExtension,
    gitHooksSetup: TaskProvider<GitHooksSetupTask>,
    ktlintTasks: KtlintTasks,
    loggerProvider: Provider<LoggerService>,
): AggregateTasks {
    val ignoredBuildTypes = extension.detekt.ignoredBuildTypes

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
        val detektTasks =
            subproject.tasks.withType(Detekt::class.java).matching { task ->
                ignoredBuildTypes.get().none { ignored -> task.name.contains(ignored, ignoreCase = true) }
            }
        pipelineCheck.configure { it.dependsOn(detektTasks) }
        prePushCheck.configure { it.dependsOn(detektTasks) }
        subproject.tasks.withType(Detekt::class.java).configureEach { task ->
            task.mustRunAfter(gitHooksSetup, ktlintTasks.check, ktlintTasks.format)
        }
    }

    return AggregateTasks(pipelineCheck, prePushCheck)
}

internal data class AggregateTasks(
    val pipelineCheck: TaskProvider<Task>,
    val prePushCheck: TaskProvider<Task>,
)
