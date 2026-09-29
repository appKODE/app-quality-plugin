package ru.kode.android.app.quality.plugin.foundation

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceTask
import ru.kode.android.app.quality.plugin.foundation.config.DetektConfig
import ru.kode.android.app.quality.plugin.foundation.config.PlatformDetektConfig
import ru.kode.android.app.quality.plugin.foundation.config.hasNoUserAdditionsProvider
import ru.kode.android.app.quality.plugin.foundation.engine.DetektEngine
import ru.kode.android.app.quality.plugin.foundation.engine.DetektSettings
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension
import ru.kode.android.app.quality.plugin.foundation.messages.bothDetektPluginsMessage
import ru.kode.android.app.quality.plugin.foundation.messages.missingDetektConfigFileMessage
import ru.kode.android.app.quality.plugin.foundation.messages.missingDependencyFileMessage
import ru.kode.android.app.quality.plugin.foundation.messages.missingKodeRuleSetDependencyMessage
import ru.kode.android.app.quality.plugin.foundation.utils.activatesKodeRuleSet
import ru.kode.android.app.quality.plugin.foundation.utils.resolveConfigFile
import ru.kode.android.app.quality.plugin.foundation.utils.wireDependencies
import ru.kode.android.app.quality.plugin.foundation.validate.validateSubprojectAgpVersion
import ru.kode.android.gradle.commons.logger.LoggerService
import java.io.File

internal val DEFAULT_DETEKT_INCLUDE_PATTERNS =
    listOf(
        "src/main/kotlin/**",
        "src/test/kotlin/**",
        "src/commonMain/kotlin/**",
        "src/commonTest/kotlin/**",
        "src/jvmMain/kotlin/**",
        "src/jvmTest/kotlin/**",
        "src/desktopMain/kotlin/**",
        "src/desktopTest/kotlin/**",
        "src/iosMain/kotlin/**",
        "src/iosTest/kotlin/**",
        "src/androidMain/kotlin/**",
        "src/androidTest/kotlin/**",
    )

internal val DEFAULT_DETEKT_EXCLUDE_PATTERNS = listOf("**/generated/**", "**/build/**")

internal fun Project.configureSubprojectsDetekt(
    extension: AppQualityFoundationExtension,
    loggerProvider: Provider<LoggerService>,
    defaults: DefaultConfigFiles,
    engine: DetektEngine,
) {
    subprojects { subproject ->
        subproject.pluginManager.withPlugin(engine.otherEnginePluginId) {
            throw GradleException(
                bothDetektPluginsMessage(subproject.path, engine.pluginId, engine.otherEnginePluginId),
            )
        }
        subproject.configureProjectDetekt(extension, loggerProvider, defaults, engine)
    }
}

internal enum class DetektPlatform { KOTLIN, ANDROID, COMPOSE }

/**
 * Configures detekt lazily per module: the detekt plugin is applied only when a matching
 * Kotlin/Android/Compose plugin is applied, and each platform config is merged exactly once
 * even when several trigger plugins are present.
 */
private fun Project.configureProjectDetekt(
    extension: AppQualityFoundationExtension,
    loggerProvider: Provider<LoggerService>,
    defaults: DefaultConfigFiles,
    engine: DetektEngine,
) {
    val configuredPlatforms = mutableSetOf<DetektPlatform>()

    fun configurePlatformOnce(
        platform: DetektPlatform,
        platformConfig: PlatformDetektConfig,
        configFileName: String,
        bundledDefault: Provider<RegularFile>,
    ) {
        if (!configuredPlatforms.add(platform)) return
        engine.applyPlugin(this)
        if (configuredPlatforms.size == 1) {
            engine.configureTasks(this, extension, loggerProvider)
        }
        val slotName = "detekt.${platform.name.lowercase()}.projectConfig"
        val configFile =
            resolveConfigFile(
                // Only an explicit override can point at a missing file; the bundled default is
                // produced by a task and legitimately absent until that task runs.
                override =
                    platformConfig.projectConfig.map { file ->
                        if (!file.asFile.exists()) {
                            throw GradleException(missingDetektConfigFileMessage(file.asFile, slotName))
                        }
                        file
                    },
                candidate = layout.projectDirectory.file(configFileName),
                bundledDefault = bundledDefault,
            )
        configureDetekt(extension, platformConfig, configFile, platform.name.lowercase(), configuredPlatforms, engine)
    }

    listOf(
        "org.jetbrains.kotlin.jvm",
        "org.jetbrains.kotlin.multiplatform",
        "org.jetbrains.kotlin.android",
        "com.android.application",
        "com.android.library",
    ).forEach { pluginId ->
        pluginManager.withPlugin(pluginId) {
            configurePlatformOnce(
                DetektPlatform.KOTLIN,
                extension.detekt.kotlin,
                "detekt-kotlin-config.yml",
                defaults.detektKotlin,
            )
        }
    }

    listOf("org.jetbrains.kotlin.android", "com.android.application", "com.android.library")
        .forEach { pluginId ->
            pluginManager.withPlugin(pluginId) {
                // The foundation plugin itself is usually applied at the root only, so
                // stopExecutionIfNotSupported never sees a subproject applying AGP directly.
                validateSubprojectAgpVersion()
                configurePlatformOnce(
                    DetektPlatform.ANDROID,
                    extension.detekt.android,
                    "detekt-android-config.yml",
                    defaults.detektAndroid,
                )
            }
        }

    listOf("org.jetbrains.compose", "org.jetbrains.kotlin.plugin.compose")
        .forEach { pluginId ->
            pluginManager.withPlugin(pluginId) {
                configurePlatformOnce(
                    DetektPlatform.COMPOSE,
                    extension.detekt.compose,
                    "detekt-compose-config.yml",
                    defaults.detektCompose,
                )
            }
        }
}

private fun Project.configureDetekt(
    extension: AppQualityFoundationExtension,
    platformConfig: PlatformDetektConfig,
    configFile: Provider<RegularFile>,
    platformName: String,
    configuredPlatforms: Set<DetektPlatform>,
    engine: DetektEngine,
) {
    configurations.named("detektPlugins").configure { detektPlugins ->
        wireDependencies(detektPlugins, platformConfig.rules) { file ->
            missingDependencyFileMessage(file, "detekt.$platformName.rules")
        }
    }

    // Composed entirely from Provider combinators: no closure captures a live domain object like
    // `extension` directly, which Gradle's configuration cache would Java-serialize wholesale.
    val noKodeJarWired = noKodeRuleSetJarWiredAnywhereProvider(extension, configuredPlatforms)
    val validatedConfigFile =
        configFile.map { file ->
            val configuredFile = file.asFile
            if (configuredFile.exists() && activatesKodeRuleSet(configuredFile) && noKodeJarWired.get()) {
                throw GradleException(missingKodeRuleSetDependencyMessage(platformName, configuredFile))
            }
            file
        }

    engine.configureExtension(this, validatedConfigFile) {
        DetektSettings(
            debug = extension.verboseLogging.get(),
            ignoredBuildTypes = extension.detekt.ignoredBuildTypes.get(),
            // Re-root under this subproject's own directory, keeping only the filename: the
            // configured `RegularFileProperty` is a single root-scoped value, so using it
            // verbatim would point every subproject at the identical file, and their
            // `detektBaseline*` tasks would overwrite each other's findings.
            baseline = extension.detekt.baseline.orNull?.let { layout.projectDirectory.file(it.asFile.name).asFile },
            buildUponDefaultConfig = extension.detekt.buildUponDefaultConfig.get(),
        )
    }
}

/**
 * `detektPlugins` is ONE configuration shared by every platform in the project, so a jar wired
 * into any platform's `rules` slot is on the classpath for all of them — this must check all 3
 * slots, not just the platform being configured, or it false-positives whenever the jar was
 * wired through a different platform (e.g. only `detekt.kotlin.rules`). `detekt.android.rules`
 * defaults to the `ru.kode:detekt-rules` coordinates, so it satisfies this check whenever
 * `useDefaults` is on — but ONLY if the android platform is actually configured for this
 * project: `useDefaults` on that slot defaults to `true` even for a project with no Android
 * module at all, where the default never reaches `detektPlugins` because `configureDetekt`
 * never runs for `ANDROID`. [configuredPlatforms] is read lazily (same mutable set the caller
 * populates during `pluginManager.withPlugin` callbacks) so by the time this actually
 * evaluates — task execution, after the whole project's configuration phase is done — it
 * reflects every platform this project ended up configuring, regardless of callback order.
 *
 * Built entirely from `Provider.map`/`.zip` — never reads `extension`/`ExternalDependencyConfig`
 * directly inside a closure passed to a consuming Provider (see the config-cache note at the
 * call site for why that matters).
 */
private fun noKodeRuleSetJarWiredAnywhereProvider(
    extension: AppQualityFoundationExtension,
    configuredPlatforms: Set<DetektPlatform>,
): Provider<Boolean> {
    val androidDefaultInactive =
        extension.detekt.android.rules.useDefaults.map { use ->
            !(DetektPlatform.ANDROID in configuredPlatforms && use)
        }
    val kotlinEmpty = extension.detekt.kotlin.rules.hasNoUserAdditionsProvider()
    val androidEmpty = extension.detekt.android.rules.hasNoUserAdditionsProvider()
    val composeEmpty = extension.detekt.compose.rules.hasNoUserAdditionsProvider()
    return androidDefaultInactive
        .zip(kotlinEmpty) { defaultInactive, kEmpty -> defaultInactive && kEmpty }
        .zip(androidEmpty) { partial, aEmpty -> partial && aEmpty }
        .zip(composeEmpty) { partial, cEmpty -> partial && cEmpty }
}

/**
 * The plain `detekt` task's sources: the whole module tree filtered by `detekt.sources`. Runs
 * when the task is realized, at task-graph time — after the consumer's extension block — so
 * extension reads observe the configured values.
 */
internal fun Project.configureDetektSources(
    task: SourceTask,
    detektConfig: DetektConfig,
) {
    val includePatterns =
        if (detektConfig.sources.useDefaults.get()) {
            DEFAULT_DETEKT_INCLUDE_PATTERNS + detektConfig.sources.include.get()
        } else {
            detektConfig.sources.include.get()
        }
    val excludePatterns = DEFAULT_DETEKT_EXCLUDE_PATTERNS + detektConfig.sources.exclude.get()
    task.source =
        fileTree(layout.projectDirectory) { tree ->
            if (includePatterns.isEmpty()) {
                // Gradle's PatternFilterable treats an empty include list as "no
                // restriction" (matches everything), so exclude everything instead.
                tree.exclude("**")
            } else {
                tree.include(includePatterns)
                tree.exclude(excludePatterns)
            }
        }
}

/** Drops generated sources from any detekt task, whatever its `source` ends up being. */
internal fun excludeGeneratedSources(task: SourceTask) {
    // Defense-in-depth: AGP/KMP Android-target variant tasks (e.g. detektAndroidDebug) have
    // their `source` reassigned later by detekt-gradle-plugin's own variant-registration
    // callback, from the AGP variant's sourceSets — which already treats the KSP output dir
    // as a first-class source root, silently overriding the exclude patterns above. A glob
    // exclude can't catch this either: for those variants the source root itself already
    // sits inside build/generated/..., so a root-relative path never contains that segment
    // again. `exclude(Spec)` is lazy and additive, evaluated against whatever `source` ends
    // up being at execution time, and matches on the absolute file path instead.
    val generatedPathMarker = "${File.separator}build${File.separator}generated${File.separator}"
    task.exclude { fileTreeElement -> fileTreeElement.file.path.contains(generatedPathMarker) }
}
