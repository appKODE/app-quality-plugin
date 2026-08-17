@file:Suppress("MatchingDeclarationName") // file groups the ktlint wiring fun with its small result type

package ru.kode.android.app.quality.plugin.foundation

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.CommandLineArgumentProvider
import ru.kode.android.app.quality.plugin.foundation.config.KtlintConfig
import ru.kode.android.app.quality.plugin.foundation.messages.missingDependencyFileMessage
import ru.kode.android.app.quality.plugin.foundation.messages.noEditorConfigFileMessage
import ru.kode.android.app.quality.plugin.foundation.utils.ignoredSourcePatterns
import ru.kode.android.app.quality.plugin.foundation.utils.kotlinSourcePatterns
import ru.kode.android.app.quality.plugin.foundation.utils.resolveConfigFile
import ru.kode.android.app.quality.plugin.foundation.utils.wireDependencies
import ru.kode.android.build.publish.plugin.core.logger.LoggerService
import java.util.concurrent.Callable

internal data class KtlintTasks(
    val check: TaskProvider<JavaExec>,
    val format: TaskProvider<JavaExec>,
)

@Suppress("LongMethod")
internal fun Project.configureKtlint(
    config: KtlintConfig,
    loggerServiceProvider: Provider<LoggerService>,
    defaultEditorConfig: Provider<RegularFile>,
): KtlintTasks {
    val ktlintCli = configurations.create("ktlintCli")
    wireDependencies(ktlintCli, config.cli) { file ->
        missingDependencyFileMessage(file, "ktlint.cli")
    }

    ktlintCli.attributes { attrs ->
        attrs.attribute(
            ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
            ArtifactTypeDefinition.JAR_TYPE,
        )
        attrs.attribute(
            Usage.USAGE_ATTRIBUTE,
            objects.named(Usage::class.java, Usage.JAVA_RUNTIME),
        )
        attrs.attribute(
            Bundling.BUNDLING_ATTRIBUTE,
            objects.named(Bundling::class.java, Bundling.SHADOWED),
        )
        attrs.attribute(
            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
            objects.named(LibraryElements::class.java, LibraryElements.JAR),
        )
        attrs.attribute(
            Category.CATEGORY_ATTRIBUTE,
            objects.named(Category::class.java, Category.LIBRARY),
        )
    }

    val validatedProjectConfig =
        config.projectConfig.map { file ->
            if (!file.asFile.exists()) {
                throw GradleException(noEditorConfigFileMessage(file.asFile))
            }
            file
        }
    val editorConfig =
        resolveConfigFile(
            override = validatedProjectConfig,
            candidate = rootProject.layout.projectDirectory.file(".editorconfig"),
            bundledDefault = defaultEditorConfig,
        )
    val ignoredPatterns = ignoredSourcePatterns(config.sources)
    val sourcePatterns = kotlinSourcePatterns(config.sources)

    fun registerKtlintTask(
        name: String,
        taskGroup: String,
        taskDescription: String,
        format: Boolean,
    ): TaskProvider<JavaExec> {
        return tasks.register(name, JavaExec::class.java) { task ->
            task.usesService(loggerServiceProvider)

            task.group = taskGroup
            task.description = taskDescription

            task.classpath = ktlintCli
            task.mainClass.set("com.pinterest.ktlint.Main")

            task.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
            )

            task.inputs.file(editorConfig)
                .withPropertyName("editorConfig")
                .withPathSensitivity(PathSensitivity.NONE)
                .optional()
            task.inputs.property("ignoredSourcePatterns", ignoredPatterns)
            task.inputs.property("sourcePatterns", sourcePatterns)

            task.doFirst {
                val editorConfigFile = editorConfig.get().asFile
                loggerServiceProvider.get().info("Use editor config for $name = ${editorConfigFile.absolutePath}")
            }

            task.argumentProviders.add(
                CommandLineArgumentProvider {
                    val editorConfigPath = editorConfig.get().asFile.absolutePath.replace('\\', '/')
                    val patterns = sourcePatterns.get()
                    buildList {
                        if (format) add("-F")
                        add("--editorconfig=$editorConfigPath")
                        add("--relative")
                        addAll(ignoredPatterns.get())
                        if (patterns.isEmpty()) {
                            // No include patterns configured: ktlint-cli falls back to its own
                            // built-in default globs when given zero positional patterns, so
                            // explicitly exclude everything to keep "nothing configured" meaning
                            // "nothing checked".
                            add("!**")
                        } else {
                            addAll(patterns)
                        }
                    }
                },
            )
        }
    }

    fun trackKotlinSourcesAsInputs(task: JavaExec) {
        task.inputs.files(
            Callable {
                fileTree(layout.projectDirectory) { tree ->
                    tree.include(sourcePatterns.get())
                    tree.exclude(ignoredPatterns.get().map { it.removePrefix("!") })
                }
            },
        )
            .withPropertyName("kotlinSources")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }

    val ktlintCheck =
        registerKtlintTask(
            name = "ktlintCheck",
            taskGroup = "verification",
            taskDescription = "Run ktlint check on all Android modules",
            format = false,
        )
    val ktlintFormat =
        registerKtlintTask(
            name = "ktlintFormat",
            taskGroup = "formatting",
            taskDescription = "Run ktlint format on all Android modules",
            format = true,
        )
    ktlintCheck.configure { task ->
        trackKotlinSourcesAsInputs(task)
        val marker = layout.buildDirectory.file("app-quality/ktlint/check-marker.txt")
        task.outputs.file(marker).withPropertyName("checkMarker")
        task.doLast {
            marker.get().asFile.writeText("ktlint check passed")
        }
    }
    ktlintFormat.configure { task ->
        trackKotlinSourcesAsInputs(task)
        val marker = layout.buildDirectory.file("app-quality/ktlint/format-marker.txt")
        task.outputs.file(marker).withPropertyName("formatMarker")
        task.doLast {
            marker.get().asFile.writeText("ktlint format applied")
        }
    }
    return KtlintTasks(
        check = ktlintCheck,
        format = ktlintFormat,
    )
}
