package ru.kode.android.app.quality.plugin.foundation

import org.gradle.api.Plugin
import org.gradle.api.Project
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension
import ru.kode.android.app.quality.plugin.foundation.validate.stopExecutionIfNotSupported
import ru.kode.android.build.publish.plugin.core.logger.LOGGER_SERVICE_EXTENSION_NAME
import ru.kode.android.build.publish.plugin.core.logger.LOGGER_SERVICE_NAME
import ru.kode.android.build.publish.plugin.core.logger.LoggerService
import ru.kode.android.build.publish.plugin.core.logger.LoggerServiceExtension
import ru.kode.android.build.publish.plugin.core.util.serviceName

const val APP_QUALITY_EXTENSION_NAME = "appQualityFoundation"

abstract class AppQualityFoundationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.stopExecutionIfNotSupported()

        val extension =
            project.extensions
                .create(APP_QUALITY_EXTENSION_NAME, AppQualityFoundationExtension::class.java)

        val defaultConfigs = project.registerDefaultConfigTasks()
        project.configureConventions(extension, defaultConfigs)

        val loggerServiceProvider =
            project.gradle.sharedServices.registerIfAbsent(
                project.serviceName(LOGGER_SERVICE_NAME),
                LoggerService::class.java,
            ) {
                it.parameters.verboseLogging.set(extension.verboseLogging)
                it.parameters.bodyLogging.set(false)
            }

        project.extensions.create(
            LOGGER_SERVICE_EXTENSION_NAME,
            LoggerServiceExtension::class.java,
            loggerServiceProvider,
        )

        project.configureSubprojectsDetekt(extension, loggerServiceProvider, defaultConfigs)
        val gitHooksSetup = project.configureGitHooksSetup(extension)
        val ktlintTasks =
            project.configureKtlint(
                extension.ktlint,
                loggerServiceProvider,
                defaultConfigs.editorconfig,
            )
        configurePrintRequiredGradleJvmargs(project)
        val aggregateTasks =
            project.configureAggregateTasks(extension, gitHooksSetup, ktlintTasks, loggerServiceProvider)
        project.configureAndroidLint(extension, aggregateTasks)
    }
}
