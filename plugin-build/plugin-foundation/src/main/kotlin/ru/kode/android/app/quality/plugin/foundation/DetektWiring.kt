package ru.kode.android.app.quality.plugin.foundation

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import ru.kode.android.app.quality.plugin.foundation.config.PlatformDetektConfig
import ru.kode.android.app.quality.plugin.foundation.config.hasNoUserAdditionsProvider
import ru.kode.android.app.quality.plugin.foundation.extension.AppQualityFoundationExtension
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
) {
    subprojects { subproject ->
        subproject.configureProjectDetekt(extension, loggerProvider, defaults)
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
) {
    val configuredPlatforms = mutableSetOf<DetektPlatform>()

    fun configurePlatformOnce(
        platform: DetektPlatform,
        platformConfig: PlatformDetektConfig,
        configFileName: String,
        bundledDefault: Provider<RegularFile>,
    ) {
        if (!configuredPlatforms.add(platform)) return
        pluginManager.apply(DetektPlugin::class.java)
        if (configuredPlatforms.size == 1) {
            configureDetektTasks(extension, loggerProvider)
        }
        val configFile =
            resolveConfigFile(
                override = platformConfig.projectConfig,
                candidate = layout.projectDirectory.file(configFileName),
                bundledDefault = bundledDefault,
            )
        configureDetekt(extension, platformConfig, configFile, platform.name.lowercase(), configuredPlatforms)
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
) {
    configurations.named("detektPlugins").configure { detektPlugins ->
        wireDependencies(detektPlugins, platformConfig.rules) { file ->
            missingDependencyFileMessage(file, "detekt.$platformName.rules")
        }
    }

    // Composed entirely from Provider combinators (no closure captures a live domain object
    // like `extension` or an `ExternalDependencyConfig` directly) — Gradle's config-cache
    // support for Provider graphs is structural, but naive closures capturing a live object
    // get naively Java-serialized instead, walking its ENTIRE reachable graph. That previously
    // broke config-cache for EVERY platform's detekt task (not just android's), because
    // `extension.detekt.android.rules.defaultFiles` (holding the bundled kode jar's
    // `FileCollectionDependency`, unserializable by Gradle) was reachable from any closure
    // that captured `extension` as a whole, even one that never actually reads that field.
    val noKodeJarWired = noKodeRuleSetJarWiredAnywhereProvider(extension, configuredPlatforms)
    val validatedConfigFile =
        configFile.map { file ->
            val configuredFile = file.asFile
            if (configuredFile.exists() && activatesKodeRuleSet(configuredFile) && noKodeJarWired.get()) {
                throw GradleException(missingKodeRuleSetDependencyMessage(platformName, configuredFile))
            }
            file
        }

    extensions.configure(DetektExtension::class.java) { detektExtension ->
        detektExtension.config.from(validatedConfigFile)
        // `extension` lives on the root project, and this callback fires while a SUBPROJECT's
        // plugins are being applied — reading the extension's Property values here directly
        // would depend on the root project's own build script having already run its
        // `appQualityFoundation { }` block, which Gradle does not guarantee relative to
        // subproject evaluation. Read immediately if the root is already evaluated (the common
        // case: root config runs before subprojects); otherwise defer to its `afterEvaluate` —
        // it cannot be registered unconditionally, since Gradle forbids `afterEvaluate` once a
        // project has finished evaluating.
        val applyExtensionValues = {
            detektExtension.debug = extension.verboseLogging.get()
            detektExtension.ignoredBuildTypes =
                (detektExtension.ignoredBuildTypes + extension.detekt.ignoredBuildTypes.get()).distinct()
            extension.detekt.baseline.orNull?.let { baseline ->
                // Re-root under this subproject's own directory, keeping only the filename: the
                // configured `RegularFileProperty` is a single root-scoped value, so using it
                // verbatim would point every subproject at the identical file, and their
                // `detektBaseline*` tasks would overwrite each other's findings.
                detektExtension.baseline = layout.projectDirectory.file(baseline.asFile.name).asFile
            }
        }
        if (rootProject.state.executed) {
            applyExtensionValues()
        } else {
            rootProject.afterEvaluate { applyExtensionValues() }
        }
    }
}

/**
 * `detektPlugins` is ONE configuration shared by every platform in the project, so a jar wired
 * into any platform's `rules` slot is on the classpath for all of them — this must check all 3
 * slots, not just the platform being configured, or it false-positives whenever the jar was
 * wired through a different platform (e.g. only `detekt.kotlin.rules`). `detekt.android.rules`
 * now has a real bundled default (the kode jar itself), so it satisfies this check whenever
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
 * Tunes detekt tasks. Runs inside `configureEach`, which fires at task-graph time — after the
 * consumer's extension block — so all extension reads here observe the configured values.
 */
private fun Project.configureDetektTasks(
    extension: AppQualityFoundationExtension,
    loggerProvider: Provider<LoggerService>,
) {
    val detektConfig = extension.detekt

    tasks.withType(DetektCreateBaselineTask::class.java).configureEach { task ->
        task.usesService(loggerProvider)
        task.jvmTarget = extension.jvmTarget.get().target
        task.debug.set(extension.verboseLogging)
    }

    tasks.withType(Detekt::class.java).configureEach { task ->
        task.usesService(loggerProvider)
        task.debug = extension.verboseLogging.get()
        task.jvmTarget = extension.jvmTarget.get().target

        if (detektConfig.typeResolution.get()) {
            val variantName = task.name.removePrefix("detekt")
            val compileTask =
                tasks.withType(KotlinJvmCompile::class.java)
                    .find { it.name.contains(variantName, ignoreCase = true) }
            if (compileTask != null) {
                task.classpath.setFrom(compileTask.libraries)
            }
        }

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

        task.reports {
            it.xml.required.set(detektConfig.xmlReportEnabled)
            it.html.required.set(false)
            it.txt.required.set(false)
            it.sarif.required.set(detektConfig.sarifReportEnabled)
        }
    }
}
