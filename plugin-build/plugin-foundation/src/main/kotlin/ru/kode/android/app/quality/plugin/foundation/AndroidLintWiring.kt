package ru.kode.android.app.quality.plugin.foundation

import org.gradle.api.Project
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension

private val ANDROID_LINT_TASK_NAME_REGEX = Regex("^lint([A-Z].*)?$")

/**
 * Opt-in wiring of AGP's own `lint`/`lint<Variant>` tasks into `pipelineCheck`/`prePushCheck`,
 * mirroring the detekt aggregate-task wiring shape (per-subproject `withType` + lazy
 * `dependsOn`). Exists mainly to prove the plugin's aggregate-task pattern can take on a new
 * tool without touching detekt/ktlint code — off by default via [AppQualityFoundationExtension
 * .androidLint], since lint is slow and most consumers already run it separately in CI.
 */
internal fun Project.configureAndroidLint(
    extension: AppQualityFoundationExtension,
    aggregateTasks: AggregateTasks,
) {
    val enabled = extension.androidLint.enabled
    val ignoredBuildTypes = extension.detekt.ignoredBuildTypes

    subprojects { subproject ->
        val lintTasks =
            subproject.tasks.matching { task ->
                enabled.get() &&
                    ANDROID_LINT_TASK_NAME_REGEX.matches(task.name) &&
                    ignoredBuildTypes.get().none { ignored -> task.name.contains(ignored, ignoreCase = true) }
            }
        aggregateTasks.pipelineCheck.configure { it.dependsOn(lintTasks) }
        aggregateTasks.prePushCheck.configure { it.dependsOn(lintTasks) }
    }
}
