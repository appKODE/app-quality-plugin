package ru.kode.android.app.quality.plugin.foundation

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.attributes.Bundling
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import ru.kode.android.app.quality.plugin.foundation.config.DetektConfig
import ru.kode.android.app.quality.plugin.foundation.config.KtlintConfig
import ru.kode.android.app.quality.plugin.foundation.config.PlatformDetektConfig
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension
import ru.kode.android.app.quality.plugin.foundation.messages.noDetektRulesDependencyReferenceInLibsMessage
import ru.kode.android.app.quality.plugin.foundation.messages.noEditorConfigFileMessage
import ru.kode.android.app.quality.plugin.foundation.messages.noKtlintDependencyReferenceInLibsMessage
import ru.kode.android.app.quality.plugin.foundation.utils.ignoredSourcePatterns
import ru.kode.android.app.quality.plugin.foundation.utils.kotlinSourcePatterns
import ru.kode.android.app.quality.plugin.foundation.utils.libs
import ru.kode.android.app.quality.plugin.foundation.utils.resolveFile
import ru.kode.android.app.quality.plugin.foundation.validate.stopExecutionIfNotSupported
import ru.kode.android.build.publish.plugin.core.logger.LOGGER_SERVICE_EXTENSION_NAME
import ru.kode.android.build.publish.plugin.core.logger.LOGGER_SERVICE_NAME
import ru.kode.android.build.publish.plugin.core.logger.LoggerService
import ru.kode.android.build.publish.plugin.core.logger.LoggerServiceExtension
import ru.kode.android.build.publish.plugin.core.util.serviceName
import java.io.File
import java.lang.management.ManagementFactory

const val APP_QUALITY_EXTENSION_NAME = "appQualityFoundation"

abstract class AppQualityFoundationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.stopExecutionIfNotSupported()

        val extension =
            project.extensions
                .create(APP_QUALITY_EXTENSION_NAME, AppQualityFoundationExtension::class.java)

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

        project.configureSubprojectsDetekt(extension, loggerServiceProvider)
        val gitHooksSetup = project.configureGitHooksSetup(extension)
        val ktlintFormat =
            project.configureKtlint(
                extension.versionCatalogName,
                extension.ktlint,
                loggerServiceProvider,
            )
        configurePrintRequiredGradleJvmargs(project)
        project.configurePrePushCheck(
            gitHooksSetup,
            extension.detekt,
            ktlintFormat,
            loggerServiceProvider,
        )
    }
}

private fun Project.configurePrePushCheck(
    gitHooksSetup: TaskProvider<Exec>,
    detektConfig: DetektConfig,
    ktlintFormat: TaskProvider<JavaExec>,
    loggerProvider: Provider<LoggerService>,
) {
    tasks.register("prePushCheck") { task ->
        task.usesService(loggerProvider)
        group = "verification"

        task.dependsOn(gitHooksSetup)
        task.dependsOn(ktlintFormat)

        subprojects.forEach { subproject ->
            subproject.tasks.withType(Detekt::class.java).configureEach { detektTask ->
                val ignored = detektConfig.ignoredBuildTypes.get()
                if (ignored.none { detektTask.name.contains(it, ignoreCase = true) }) {
                    task.dependsOn(detektTask)
                    detektTask.mustRunAfter(gitHooksSetup, ktlintFormat)
                }
            }
        }
    }
}

private fun configurePrintRequiredGradleJvmargs(project: Project) {
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

private fun Project.configureGitHooksSetup(extension: AppQualityFoundationExtension): TaskProvider<Exec> {
    val hooksPath =
        extension.gitHooks.getOrElse {
            project.rootProject.file(".githooks")
        }.asFile
    return tasks.register("gitHooksSetup", Exec::class.java) { task ->
        task.executable = "sh"
        task.args = listOf("-c", "git config core.hooksPath ${hooksPath.path}")
        task.description = "Changing hookspath to project .githooks"
    }
}

private fun Project.configureKtlint(
    versionCatalogName: Property<String>,
    config: KtlintConfig,
    loggerServiceProvider: Provider<LoggerService>,
): TaskProvider<JavaExec> {
    val ktlintCli = configurations.create("ktlintCli")

    val ktlintLibraryName = config.cliLibraryName.get()
    val additionalIgnoredSourcePatterns = config.additionalIgnoredSourcePatterns.get()
    val additionalSourcePatterns = config.additionalSourcePatterns.get()

    val ktlintProjectConfigPath =
        config.projectConfig.getOrElse {
            rootProject.file(".editorconfig")
        }
    val ktlintFallbackFile =
        layout.buildDirectory
            .file("ktlint/.editorconfig")
            .get()
    val ktlintDefaultFile = "ktlint/default.editorconfig"

    val editorConfigProvider =
        resolveFile(
            ktlintProjectConfigPath,
            ktlintFallbackFile,
            ktlintDefaultFile,
            loggerServiceProvider,
        )

    dependencies.add(
        ktlintCli.name,
        libs(versionCatalogName.get())
            .findLibrary(ktlintLibraryName)
            .orElseThrow {
                GradleException(noKtlintDependencyReferenceInLibsMessage(ktlintLibraryName))
            },
    )

    ktlintCli.attributes { attrs ->
        attrs.attribute(
            Bundling.BUNDLING_ATTRIBUTE,
            objects.named(Bundling::class.java, Bundling.EXTERNAL),
        )
    }

    val ignoredSourcePatterns = ignoredSourcePatterns(additionalIgnoredSourcePatterns)
    val kotlinSourcePatterns = kotlinSourcePatterns(additionalSourcePatterns)

    tasks.register("ktlintCheck", JavaExec::class.java) { task: JavaExec ->
        task.usesService(loggerServiceProvider)

        task.group = "verification"
        task.description = "Run ktlint check on all Android modules"

        task.classpath = ktlintCli
        task.mainClass.set("com.pinterest.ktlint.Main")

        task.jvmArgs(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
        )

        task.doFirst {
            val editorConfig = editorConfigProvider.get().asFile

            if (!editorConfig.exists()) {
                throw GradleException(noEditorConfigFileMessage(editorConfig))
            }

            val editorConfigPath = editorConfig.absolutePath.replace('\\', '/')

            val logger = loggerServiceProvider.get()
            logger.info("Use editor config for ktlintCheck = $editorConfigPath")

            task.args = listOf(
                "--editorconfig=$editorConfigPath",
                "--relative",
            ) + ignoredSourcePatterns + kotlinSourcePatterns
        }
    }

    val ktlintFormat =
        tasks.register("ktlintFormat", JavaExec::class.java) { task ->
            task.usesService(loggerServiceProvider)

            task.group = "formatting"
            task.description = "Run ktlint format on all Android modules"

            task.classpath = ktlintCli
            task.mainClass.set("com.pinterest.ktlint.Main")

            task.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
            )

            task.doFirst {
                val editorConfig = editorConfigProvider.get().asFile

                if (!editorConfig.exists()) {
                    throw GradleException(noEditorConfigFileMessage(editorConfig))
                }

                val editorConfigPath = editorConfig.absolutePath.replace('\\', '/')

                val logger = loggerServiceProvider.get()
                logger.info("do first editor config path $editorConfigPath")

                task.args = listOf(
                    "-F",
                    "--editorconfig=$editorConfigPath",
                    "--relative",
                ) + ignoredSourcePatterns + kotlinSourcePatterns
            }
        }
    return ktlintFormat
}

private fun Project.configureSubprojectsDetekt(
    extension: AppQualityFoundationExtension,
    loggerProvider: Provider<LoggerService>,
) {
    subprojects { subproject ->
        subproject.configureProjectDetekt(extension, loggerProvider)
    }
}

private fun Project.configureProjectDetekt(
    extension: AppQualityFoundationExtension,
    loggerProvider: Provider<LoggerService>,
) {
    listOf("org.jetbrains.kotlin.jvm", "org.jetbrains.kotlin.multiplatform", "com.android.library")
        .forEach { pluginId ->
            pluginManager.apply(DetektPlugin::class.java)
            pluginManager.withPlugin(pluginId) {
                val config = kotlinDetektConfig(extension, loggerProvider)
                configureDetekt(
                    loggerProvider = loggerProvider,
                    verboseLogging = extension.verboseLogging,
                    versionCatalogName = extension.versionCatalogName,
                    config = config,
                    detektConfig = extension.detekt,
                )
            }
        }

    listOf("org.jetbrains.kotlin.android", "com.android.application")
        .forEach { pluginId ->
            pluginManager.apply(DetektPlugin::class.java)
            pluginManager.withPlugin(pluginId) {
                val kotlinConfig = kotlinDetektConfig(extension, loggerProvider)
                configureDetekt(
                    loggerProvider = loggerProvider,
                    verboseLogging = extension.verboseLogging,
                    versionCatalogName = extension.versionCatalogName,
                    config = kotlinConfig,
                    detektConfig = extension.detekt,
                )
                val androidConfig = androidDetektConfig(extension, loggerProvider)
                configureDetekt(
                    loggerProvider = loggerProvider,
                    verboseLogging = extension.verboseLogging,
                    versionCatalogName = extension.versionCatalogName,
                    config = androidConfig,
                    detektConfig = extension.detekt,
                )
            }
        }

    listOf("org.jetbrains.compose", "org.jetbrains.kotlin.plugin.compose")
        .forEach { pluginId ->
            pluginManager.apply(DetektPlugin::class.java)
            pluginManager.withPlugin(pluginId) {
                val config = composeDetektConfig(extension, loggerProvider)
                configureDetekt(
                    loggerProvider = loggerProvider,
                    verboseLogging = extension.verboseLogging,
                    versionCatalogName = extension.versionCatalogName,
                    config = config,
                    detektConfig = extension.detekt,
                )
            }
        }

    configureDetektTasks(
        verboseLogging = extension.verboseLogging,
        jvmTarget = extension.jvmTarget,
        loggerProvider = loggerProvider,
        detektConfig = extension.detekt,
    )
}

private fun Project.composeDetektConfig(
    extension: AppQualityFoundationExtension,
    loggerProvider: Provider<LoggerService>,
): PlatformDetektConfig {
    return extension.detekt.compose.also {
        it.rulesLibraryNames.convention(listOf("detekt.compose.rules"))

        val detektProjectFile =
            layout.projectDirectory
                .file("detekt-compose-config.yml")
        val detektFallbackFile =
            layout.buildDirectory
                .file("detekt/compose-config.yml")
                .get()
        val detektDefaultFile = "detekt/default.compose-config.yml"
        val detektConfigProvider =
            resolveFile(
                detektProjectFile,
                detektFallbackFile,
                detektDefaultFile,
                loggerProvider,
            )
        it.projectConfig.convention(detektConfigProvider)
    }
}

private fun Project.androidDetektConfig(
    extension: AppQualityFoundationExtension,
    loggerProvider: Provider<LoggerService>,
): PlatformDetektConfig {
    return extension.detekt.android.also {
        val detektProjectFile =
            layout.projectDirectory
                .file("detekt-android-config.yml")
        val detektFallbackFile =
            layout.buildDirectory
                .file("detekt/android-config.yml")
                .get()
        val detektDefaultFile = "detekt/default.android-config.yml"
        val detektConfigProvider =
            resolveFile(
                detektProjectFile,
                detektFallbackFile,
                detektDefaultFile,
                loggerProvider,
            )
        it.projectConfig.convention(detektConfigProvider)
    }
}

private fun Project.kotlinDetektConfig(
    extension: AppQualityFoundationExtension,
    loggerProvider: Provider<LoggerService>,
): PlatformDetektConfig {
    return extension.detekt.kotlin.also {
        it.rulesPluginJar.convention {
            project.rootProject.file("libs/detekt-rules-1.4.0.jar")
        }

        it.rulesLibraryNames.convention(listOf("detekt.formatting"))

        val detektProjectFile =
            layout.projectDirectory
                .file("detekt-kotlin-config.yml")
        val detektFallbackFile =
            layout.buildDirectory
                .file("detekt/kotlin-config.yml")
                .get()
        val detektDefaultFile = "detekt/default.kotlin-config.yml"
        val detektConfigProvider =
            resolveFile(
                detektProjectFile,
                detektFallbackFile,
                detektDefaultFile,
                loggerProvider,
            )
        it.projectConfig.convention(detektConfigProvider)
    }
}

private fun Project.configureDetekt(
    loggerProvider: Provider<LoggerService>,
    verboseLogging: Provider<Boolean>,
    versionCatalogName: Provider<String>,
    config: PlatformDetektConfig,
    detektConfig: DetektConfig,
) {
    val logger = loggerProvider.orNull
    val detektRulesPluginJars = config.rulesPluginJar.orNull
    val detektProjectConfigPath = config.projectConfig.orNull
    val detektLibraries = config.rulesLibraryNames.get()

    val detektPlugins = configurations.getAt("detektPlugins")

    detektRulesPluginJars?.asFile?.let { jar ->
        if (!detektPlugins.dependencies.any { it is FileCollectionDependency && it.files.contains(jar) }) {
            logger?.info("Adding detekt plugin jar $jar")
            dependencies.add(detektPlugins.name, files(jar))
        } else {
            logger?.info("SKIP adding detekt plugin jar $jar")
        }
    }

    detektLibraries.forEach { detektLibraryName ->
        val lib =
            libs(versionCatalogName.get())
                .findLibrary(detektLibraryName)
                .orElseThrow { GradleException(noDetektRulesDependencyReferenceInLibsMessage(detektLibraryName)) }
                .get()

        if (!detektPlugins.dependencies.any {
                it is ExternalModuleDependency && it.group == lib.module.group && it.name == lib.module.name
            }
        ) {
            logger?.info("Adding detekt plugin library $lib")
            dependencies.add(detektPlugins.name, lib)
        } else {
            logger?.info("SKIP adding detekt plugin library $lib")
        }
    }

    extensions.configure(DetektExtension::class.java) { detektExtension ->
        detektProjectConfigPath?.asFile?.let { configFile ->
            if (!detektExtension.config.contains(configFile)) {
                logger?.info("Adding detekt config $configFile")
                val mergedConfig = (detektExtension.config.files + configFile).distinct()
                logger?.info("Adding merged config files $mergedConfig")
                detektExtension.config.from(mergedConfig)
            } else {
                logger?.info("SKIP adding detekt config $configFile")
            }
        }

        detektExtension.debug = verboseLogging.get()
        val ignoredBuildTypes = detektConfig.ignoredBuildTypes.get()
        val mergedIgnoredBuiltTypes = (detektExtension.ignoredBuildTypes + ignoredBuildTypes).distinct()
        detektExtension.ignoredBuildTypes =
            mergedIgnoredBuiltTypes.also {
                logger?.info("Detekt ignoredBuildTypes = $it")
            }
    }
}

private fun Project.configureDetektTasks(
    verboseLogging: Provider<Boolean>,
    jvmTarget: Provider<JvmTarget>,
    loggerProvider: Provider<LoggerService>,
    detektConfig: DetektConfig,
) {
    val jvmTargetProvider =
        (
            tasks.withType(KotlinJvmCompile::class.java)
                .firstOrNull()
                ?.compilerOptions
                ?.jvmTarget
                ?.convention(jvmTarget)
                ?: jvmTarget
        )
            .map { it.target }

    tasks.withType(DetektCreateBaselineTask::class.java).configureEach { task ->
        task.usesService(loggerProvider)
        task.jvmTarget = jvmTargetProvider.get()
        task.debug.set(verboseLogging)

        task.doFirst {
            val logger = loggerProvider.get()
            logger.info("Detekt baseline task ${task.name}")
        }
    }

    tasks.withType(Detekt::class.java).configureEach { task ->
        task.usesService(loggerProvider)
        task.debug = verboseLogging.get()

        val compileTask =
            tasks.withType(KotlinJvmCompile::class.java)
                .find { it.name.contains(task.name.removePrefix("detekt"), ignoreCase = true) }

        if (compileTask != null && detektConfig.typeResolution.get()) {
            task.classpath.setFrom(compileTask.libraries)
        }

        task.jvmTarget = jvmTargetProvider.get()

        task.exclude { fileTreeElement ->
            val sep = File.separator
            val absolutePath = fileTreeElement.file.absolutePath

            absolutePath.contains("${sep}generated$sep") ||
                absolutePath.contains("${sep}build$sep") ||
                detektConfig.additionallyExcludedPaths.get().any { absolutePath.contains("${sep}${it}$sep") }
        }

        val sourcesPaths: List<String> =
            listOf(
                "src/main/kotlin",
                "src/test/kotlin",
                "src/commonMain/kotlin",
                "src/commonTest/kotlin",
                "src/desktopMain/kotlin",
                "src/desktopTest/kotlin",
                "src/iosMain/kotlin",
                "src/iosTest/kotlin",
                "src/androidMain/kotlin",
                "src/androidTest/kotlin",
            ) + detektConfig.additionalSourcePaths.get()

        task.source(files(sourcesPaths))

        task.reports {
            it.xml.required.set(false)
            it.html.required.set(false)
            it.txt.required.set(false)
            it.sarif.required.set(false)
        }

        task.doFirst {
            val logger = loggerProvider.get()
            logger.info("Detekt task ${task.name}")
        }
    }
}
