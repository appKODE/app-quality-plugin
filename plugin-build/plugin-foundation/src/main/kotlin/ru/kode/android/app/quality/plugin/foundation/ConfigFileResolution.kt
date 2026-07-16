package ru.kode.android.app.quality.plugin.foundation

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension
import ru.kode.android.app.quality.plugin.foundation.task.GenerateDefaultConfigFileTask
import ru.kode.android.app.quality.plugin.foundation.task.GenerateDefaultRulesJarTask
import ru.kode.android.app.quality.plugin.foundation.utils.catalogLibraryOrDefault
import java.util.Base64
import java.util.Properties

internal const val KODE_ANDROID_RULES_JAR_NAME = "kode-android-rules-1.4.0.jar"

private val defaultToolVersions: Properties by lazy { readBundledProperties("default-tool-versions.properties") }

internal fun defaultToolVersion(alias: String): String =
    defaultToolVersions.getProperty(alias)
        ?: throw GradleException(
            "Default tool version for '$alias' not found in bundled default-tool-versions.properties",
        )

internal data class DefaultConfigFiles(
    val detektKotlin: Provider<RegularFile>,
    val detektAndroid: Provider<RegularFile>,
    val detektCompose: Provider<RegularFile>,
    val detektAndroidRulesJar: Provider<RegularFile>,
    val editorconfig: Provider<RegularFile>,
)

/**
 * Sets lazy conventions and seeds the dependency slots' defaults right after the extension is
 * created. Catalog lookups are deferred until first use, and defaults are queried only while
 * the slot's `useDefaults` is true. Each slot prefers a matching alias from the consumer's own
 * `libs` catalog when present, falling back to a coordinate baked into the plugin otherwise —
 * so the plugin works with zero catalog setup, while an existing project's pinned version (if
 * declared) keeps winning unchanged.
 */
internal fun Project.configureConventions(
    extension: AppQualityFoundationExtension,
    defaultConfigs: DefaultConfigFiles,
) {
    extension.gitHooks.convention(rootProject.layout.projectDirectory.file(".githooks"))
    extension.ktlint.cli.defaults.add(
        catalogLibraryOrDefault("ktlint-cli", defaultToolVersion("ktlint-cli")),
    )
    extension.detekt.kotlin.rules.defaults.add(
        catalogLibraryOrDefault("detekt-formatting", defaultToolVersion("detekt-formatting")),
    )
    extension.detekt.compose.rules.defaults.add(
        catalogLibraryOrDefault("detekt-compose-rules", defaultToolVersion("detekt-compose-rules")),
    )
    extension.detekt.android.rules.defaultFiles.add(files(defaultConfigs.detektAndroidRulesJar))
}

/**
 * Registers root-level tasks that materialize the bundled default configs into the build
 * directory. Consumers depend on the outputs through providers, so the tasks run only when
 * a default is actually needed and nothing is written at configuration time.
 */
internal fun Project.registerDefaultConfigTasks(): DefaultConfigFiles {
    fun register(
        taskName: String,
        resourcePath: String,
        outputPath: String,
    ): Provider<RegularFile> {
        val task =
            tasks.register(taskName, GenerateDefaultConfigFileTask::class.java) { t ->
                t.resourceContent.set(providers.provider { readBundledResource(resourcePath) })
                t.outputFile.set(layout.buildDirectory.file(outputPath))
            }
        return task.flatMap { it.outputFile }
    }

    fun registerJar(
        taskName: String,
        resourcePath: String,
        outputPath: String,
    ): Provider<RegularFile> {
        val task =
            tasks.register(taskName, GenerateDefaultRulesJarTask::class.java) { t ->
                t.resourceContentBase64.set(
                    providers.provider {
                        Base64.getEncoder().encodeToString(readBundledResourceBytes(resourcePath))
                    },
                )
                t.outputFile.set(layout.buildDirectory.file(outputPath))
            }
        return task.flatMap { it.outputFile }
    }
    return DefaultConfigFiles(
        detektKotlin =
            register(
                "generateDefaultDetektKotlinConfig",
                "detekt/default.kotlin-config.yml",
                "app-quality/detekt/kotlin-config.yml",
            ),
        detektAndroid =
            register(
                "generateDefaultDetektAndroidConfig",
                "detekt/default.android-config.yml",
                "app-quality/detekt/android-config.yml",
            ),
        detektCompose =
            register(
                "generateDefaultDetektComposeConfig",
                "detekt/default.compose-config.yml",
                "app-quality/detekt/compose-config.yml",
            ),
        detektAndroidRulesJar =
            registerJar(
                "generateDefaultDetektAndroidRulesJar",
                "detekt/rules/$KODE_ANDROID_RULES_JAR_NAME",
                "app-quality/detekt/rules/$KODE_ANDROID_RULES_JAR_NAME",
            ),
        editorconfig =
            register(
                "generateDefaultKtlintEditorconfig",
                "ktlint/default.editorconfig",
                "app-quality/ktlint/.editorconfig",
            ),
    )
}

private fun readBundledResource(path: String): String {
    return PluginResources::class.java.getResourceAsStream(path)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: throw GradleException("Default file ($path) not found in plugin resources")
}

private fun readBundledProperties(path: String): Properties {
    val stream =
        PluginResources::class.java.getResourceAsStream(path)
            ?: throw GradleException("Default file ($path) not found in plugin resources")
    return stream.use { Properties().apply { load(it) } }
}

private fun readBundledResourceBytes(path: String): ByteArray {
    return PluginResources::class.java.getResourceAsStream(path)
        ?.use { it.readBytes() }
        ?: throw GradleException("Default file ($path) not found in plugin resources")
}
