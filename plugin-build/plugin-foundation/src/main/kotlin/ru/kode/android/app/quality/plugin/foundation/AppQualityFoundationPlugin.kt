package ru.kode.android.app.quality.plugin.foundation

import org.gradle.api.Plugin
import org.gradle.api.Project
import ru.kode.android.app.quality.plugin.foundation.engine.resolveDetektEngine
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension
import ru.kode.android.app.quality.plugin.foundation.validate.stopExecutionIfNotSupported
import ru.kode.android.gradle.commons.logger.LOGGER_SERVICE_EXTENSION_NAME
import ru.kode.android.gradle.commons.logger.LOGGER_SERVICE_NAME
import ru.kode.android.gradle.commons.logger.LoggerService
import ru.kode.android.gradle.commons.logger.LoggerServiceExtension
import ru.kode.android.gradle.commons.util.serviceName

const val APP_QUALITY_EXTENSION_NAME = "appQualityFoundation"

abstract class AppQualityFoundationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // AGP is compileOnly: a JVM-only build has none of its classes, so only probe it when present.
        if (project.extensions.findByName("androidComponents") != null) project.stopExecutionIfNotSupported()

        val extension =
            project.extensions
                .create(APP_QUALITY_EXTENSION_NAME, AppQualityFoundationExtension::class.java)

        val engine = project.resolveDetektEngine()
        val defaultConfigs = project.registerDefaultConfigTasks(engine)
        project.configureConventions(extension, engine)
        project.warnAboutLegacyRulesJars()

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

        project.configureSubprojectsDetekt(extension, loggerServiceProvider, defaultConfigs, engine)
        val gitHooksSetup = project.configureGitHooksSetup(extension)
        val ktlintTasks =
            project.configureKtlint(
                extension.ktlint,
                loggerServiceProvider,
                defaultConfigs.editorconfig,
            )
        configurePrintRequiredGradleJvmargs(project)
        val aggregateTasks =
            project.configureAggregateTasks(extension, gitHooksSetup, ktlintTasks, loggerServiceProvider, engine)
        project.configureAndroidLint(extension, aggregateTasks)
    }
}
