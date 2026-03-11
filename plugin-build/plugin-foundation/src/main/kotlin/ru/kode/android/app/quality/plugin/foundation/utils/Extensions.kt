package ru.kode.android.app.quality.plugin.foundation.utils

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import ru.kode.android.app.quality.plugin.core.logger.LoggerService

internal fun kotlinSourcePatterns(
    additionalSourcePatterns: List<String> = emptyList()
): List<String> {
    return listOf(
        "**/src/*/java/**/*.kt",
        "**/src/*/kotlin/**/*.kt"
    ) + additionalSourcePatterns
}

internal fun ignoredSourcePatterns(
    additionalIgnoredSourcePatterns: List<String> = emptyList()
): List<String> {
    return listOf(
        "!**/build/**",
        "!**/generated/**",
        "!**/templates/**",
        "!**/src/test/**",
        "!**/src/androidTest/**",
        "!**/src/commonTest/**",
        "!templates/**",
        "!**/schema/**/*.kt"
    ) + additionalIgnoredSourcePatterns
}

internal fun Project.libs(catalogName: String): VersionCatalog {
    return this.extensions.getByType(VersionCatalogsExtension::class.java)
        .named(catalogName)
}

internal fun Project.resolveFile(
    projectRegularFile: RegularFile,
    fallbackRegularFile: RegularFile,
    defaultFile: String,
    loggerProvider: Provider<LoggerService>
): Provider<RegularFile> {
    if (projectRegularFile.asFile.exists()) return provider { projectRegularFile }

    val logger = loggerProvider.get()
    val fallbackFile = fallbackRegularFile.asFile

    if (!fallbackFile.exists()) {
        fallbackFile.parentFile.mkdirs()

        PluginResources::class.java.getResourceAsStream(defaultFile)
            ?.use { input ->
                fallbackFile.outputStream().use { output ->
                    input.copyTo(output)
                    logger.info("Copied ${input.available()} bytes to $fallbackFile")
                }
            }
            ?: throw GradleException("Default file (${defaultFile}) not found in plugin resources")
    }

    logger.info("Using default file (${fallbackFile} from AppQualityFoundationPlugin")
    return provider { fallbackRegularFile }
}

internal fun Project.isComposeEnabled(): Boolean {
    val android = extensions.findByType(CommonExtension::class.java) ?: return false
    return android.buildFeatures.compose == true
}
