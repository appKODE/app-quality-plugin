package ru.kode.android.app.quality.plugin.foundation

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
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
        project.configurePrePushCheck(gitHooksSetup, ktlintFormat, loggerServiceProvider)
    }
}

private fun Project.configurePrePushCheck(
    gitHooksSetup: TaskProvider<Exec>,
    ktlintFormat: TaskProvider<JavaExec>,
    loggerProvider: Provider<LoggerService>,
) {
    val logger = loggerProvider.get()

    project.tasks.register("prePushCheck") { task ->
        task.group = "verification"

        val detektTasks =
            subprojects.flatMap { subproject ->
                subproject.tasks.withType(Detekt::class.java)
            }

        if (detektTasks.isEmpty()) {
            logger.quiet("Detekt tasks not found in subprojects, skipping Detekt")
        }

        task.dependsOn(
            gitHooksSetup,
            ktlintFormat,
            detektTasks,
        )

        detektTasks.forEach { detekt ->
            detekt.mustRunAfter(gitHooksSetup, ktlintFormat)
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
    loggerProvider: Provider<LoggerService>,
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
            loggerProvider,
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
        task.group = "verification"
        task.description = "Run ktlint check on all Android modules"

        task.classpath = ktlintCli
        task.mainClass.set("com.pinterest.ktlint.Main")

        task.jvmArgs(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
        )

        val editorConfig = editorConfigProvider.get().asFile

        task.doFirst {
            if (!editorConfig.exists()) {
                throw GradleException(noEditorConfigFileMessage(editorConfig))
            }

            val editorConfigPath = editorConfig.absolutePath.replace('\\', '/')

            logger.info("Use editor config for ktlintCheck = $editorConfigPath")

            task.args = listOf(
                "--editorconfig=$editorConfigPath",
                "--relative",
            ) + ignoredSourcePatterns + kotlinSourcePatterns
        }
    }

    val ktlintFormat =
        tasks.register("ktlintFormat", JavaExec::class.java) { task ->
            task.group = "formatting"
            task.description = "Run ktlint format on all Android modules"

            task.classpath = ktlintCli
            task.mainClass.set("com.pinterest.ktlint.Main")

            task.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
            )

            val editorConfig = editorConfigProvider.get().asFile

            task.doFirst {
                if (!editorConfig.exists()) {
                    throw GradleException(noEditorConfigFileMessage(editorConfig))
                }

                val editorConfigPath = editorConfig.absolutePath.replace('\\', '/')

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
    val logger = loggerProvider.get()

    logger.info("configure subproject $name")

    afterEvaluate {
        val isKotlin =
            pluginManager.hasPlugin("org.jetbrains.kotlin.jvm") ||
                pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform") ||
                pluginManager.hasPlugin("com.android.library") ||
                pluginManager.hasPlugin("com.android.application")
        val isAndroid =
            pluginManager.hasPlugin("org.jetbrains.kotlin.android") ||
                pluginManager.hasPlugin("org.jetbrains.kotlin.android") ||
                pluginManager.hasPlugin("com.android.application")
        val isCompose =
            pluginManager.hasPlugin("org.jetbrains.compose") ||
                pluginManager.hasPlugin("org.jetbrains.kotlin.plugin.compose")

        configureDetekt(
            extension.jvmTarget,
            extension.versionCatalogName,
            configs =
                listOfNotNull(
                    extension.detekt.kotlin.takeIf { isKotlin }?.also {
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
                    },
                    extension.detekt.android.takeIf { isAndroid }?.also {
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
                    },
                    extension.detekt.compose.takeIf { isCompose }?.also {
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
                    },
                ),
            loggerProvider = loggerProvider,
            ignoredBuildTypes = extension.detekt.ignoredBuildTypes,
            additionallyExcludedPaths = extension.detekt.additionallyExcludedPaths,
        )
    }
}

private fun Project.configureDetekt(
    jvmTarget: Property<JvmTarget>,
    versionCatalogName: Property<String>,
    configs: List<PlatformDetektConfig>,
    loggerProvider: Provider<LoggerService>,
    ignoredBuildTypes: ListProperty<String>,
    additionallyExcludedPaths: ListProperty<String>,
) {
    val logger = loggerProvider.get()

    logger.info("Configure detekt ${project.path}")

    val detektRulesPluginJars =
        configs.mapNotNull { config ->
            config.rulesPluginJar.orNull?.asFile
        }
    val detektProjectConfigPaths =
        configs.mapNotNull { config ->
            config.projectConfig.orNull?.asFile
        }
    val detektAdditionallyExcludedPaths = additionallyExcludedPaths.get()

    pluginManager.apply(DetektPlugin::class.java)

    val detektPlugins = configurations.getAt("detektPlugins")

    if (detektRulesPluginJars.isNotEmpty()) {
        detektRulesPluginJars.forEach { logger.info("Apply jar file $it") }
        dependencies.add(
            detektPlugins.name,
            files(detektRulesPluginJars),
        )
    }

    val detektLibraries =
        configs.flatMap { config ->
            config.rulesLibraryNames.get()
        }

    detektLibraries.forEach { detektLibraryName ->
        dependencies.add(
            detektPlugins.name,
            libs(versionCatalogName.get())
                .findLibrary(detektLibraryName)
                .orElseThrow {
                    GradleException(noDetektRulesDependencyReferenceInLibsMessage(detektLibraryName))
                },
        )
    }

    extensions.configure(DetektExtension::class.java) { detektExtension ->
        logger.info("Configure detekt extension ${project.path}")
        if (detektProjectConfigPaths.isNotEmpty()) {
            detektExtension.config.from(detektProjectConfigPaths)
        }
        detektExtension.ignoredBuildTypes = ignoredBuildTypes.get()
    }

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
        logger.info("Detekt baseline task ${task.name}")
        task.jvmTarget = jvmTargetProvider.get()
    }

    tasks.withType(Detekt::class.java).configureEach { task ->
        logger.info("Detekt task ${task.name}")
        task.jvmTarget = jvmTargetProvider.get()

        task.exclude { fileTreeElement ->
            val sep = File.separator
            val absolutePath = fileTreeElement.file.absolutePath

            absolutePath.contains("${sep}generated$sep") ||
                detektAdditionallyExcludedPaths.any { it.contains(absolutePath) }
        }

        task.reports {
            it.xml.required.set(false)
            it.html.required.set(false)
            it.txt.required.set(false)
            it.sarif.required.set(false)
        }
    }
}
