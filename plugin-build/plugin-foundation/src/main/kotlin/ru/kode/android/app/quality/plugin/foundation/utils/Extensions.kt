package ru.kode.android.app.quality.plugin.foundation.utils

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import ru.kode.android.app.quality.plugin.foundation.config.ExternalDependencyConfig
import ru.kode.android.app.quality.plugin.foundation.config.SourcePatternsConfig
import java.io.File

internal const val VERSION_CATALOG_NAME = "libs"

private val DEFAULT_KOTLIN_INCLUDE_PATTERNS =
    listOf(
        "**/src/*/java/**/*.kt",
        "**/src/*/kotlin/**/*.kt",
    )

private val DEFAULT_KOTLIN_EXCLUDE_PATTERNS =
    listOf(
        "**/build/**",
        "**/generated/**",
        "**/templates/**",
        "**/src/test/**",
        "**/src/androidTest/**",
        "**/src/commonTest/**",
        "templates/**",
        "**/schema/**/*.kt",
    )

/**
 * Builds the ktlint include-pattern list from [sources]: the bundled Kotlin globs (while
 * `useDefaults` is true) plus any user-added `include` patterns.
 */
internal fun kotlinSourcePatterns(sources: SourcePatternsConfig): Provider<List<String>> {
    return sources.useDefaults.flatMap { useDefaults ->
        sources.include.map { (if (useDefaults) DEFAULT_KOTLIN_INCLUDE_PATTERNS else emptyList()) + it }
    }
}

/**
 * Builds the ktlint ignore-pattern list from [sources]: the bundled ignore globs (while
 * `useDefaults` is true) plus any user-added `exclude` patterns — each prefixed with `!`
 * for the ktlint-CLI ignore syntax (the DSL itself takes bare patterns).
 */
internal fun ignoredSourcePatterns(sources: SourcePatternsConfig): Provider<List<String>> {
    return sources.useDefaults.flatMap { useDefaults ->
        sources.exclude.map { excluded ->
            ((if (useDefaults) DEFAULT_KOTLIN_EXCLUDE_PATTERNS else emptyList()) + excluded)
                .map { "!$it" }
        }
    }
}

/**
 * Lazily resolves a library: a matching alias in the consumer's own `libs` version catalog if
 * present, otherwise [fallbackCoordinate] baked into the plugin. The lookup is deferred until
 * the returned provider is queried, so a project that never runs the consuming task — or that
 * configures the dependency explicitly — never pays for it. Never fails: an absent catalog or
 * alias is exactly when the fallback applies.
 */
internal fun Project.catalogLibraryOrDefault(
    alias: String,
    fallbackCoordinate: String,
): Provider<Dependency> {
    return providers.provider {
        val fromCatalog: Provider<Dependency>? =
            extensions.findByType(VersionCatalogsExtension::class.java)
                ?.find(VERSION_CATALOG_NAME)
                ?.orElse(null)
                ?.findLibrary(alias)
                ?.orElse(null)
                ?.map<Dependency> { it }
        fromCatalog ?: providers.provider { dependencies.create(fallbackCoordinate) }
    }.flatMap { it }
}

/**
 * Wires an [ExternalDependencyConfig] slot into a configuration — the ONE mechanism every
 * external-dependency slot of the plugin uses. User additions (any source kind) are wired
 * with fail-fast validation of file entries; the slot's defaults are added independently,
 * controlled by `useDefaults`.
 */
internal fun Project.wireDependencies(
    configuration: Configuration,
    slot: ExternalDependencyConfig,
    missingFileMessage: (File) -> String,
) {
    // Validation must stay side-effect-free (read + throw only) for configuration-cache safety.
    configuration.dependencies.addAllLater(
        slot.from.dependencies.map { dependencies ->
            dependencies.onEach { dependency ->
                if (dependency is FileCollectionDependency) {
                    dependency.files.forEach { file ->
                        if (!file.exists()) throw GradleException(missingFileMessage(file))
                    }
                }
            }
        },
    )
    // Match fromDependencyCollector semantics: constraints added via from.addConstraint are wired too.
    configuration.dependencyConstraints.addAllLater(slot.from.dependencyConstraints)
    configuration.dependencies.addAllLater(
        // flatMap, NOT zip: defaults are throwing catalog lookups and must never be
        // evaluated when useDefaults is false.
        slot.useDefaults.flatMap { use ->
            if (use) slot.defaults else providers.provider { emptyList<Dependency>() }
        },
    )
    // File-based defaults (see ExternalDependencyConfig.defaultFiles) go through the same
    // DependencyCollector mechanism as `from`, not the plain `defaults` ListProperty above —
    // required for configuration-cache compatibility.
    configuration.dependencies.addAllLater(
        slot.useDefaults.flatMap { use ->
            if (use) slot.defaultFiles.dependencies else providers.provider { emptySet<Dependency>() }
        },
    )
    configuration.dependencyConstraints.addAllLater(
        slot.useDefaults.flatMap { use ->
            if (use) slot.defaultFiles.dependencyConstraints else providers.provider { emptySet() }
        },
    )
}

/**
 * Resolves a config file with the plugin's standard priority:
 * 1. an explicit user override from the extension,
 * 2. the conventional project-level file, if it exists,
 * 3. the bundled default materialized by a [GenerateDefaultConfigFileTask]
 *    (the provider carries the task dependency).
 *
 * The existence probe of [candidate] runs when the provider is queried; Gradle tracks it
 * as a configuration input, so creating the file later correctly invalidates the
 * configuration cache. Nothing is written at configuration time.
 */
internal fun Project.resolveConfigFile(
    override: Provider<RegularFile>,
    candidate: RegularFile,
    bundledDefault: Provider<RegularFile>,
): Provider<RegularFile> {
    val candidateIfExists = providers.provider { candidate.takeIf { it.asFile.exists() } }
    return override
        .orElse(candidateIfExists)
        .orElse(bundledDefault)
}

private val KODE_RULE_SET_KEY_REGEX = Regex("(?m)^kode:")

/**
 * Whether a resolved detekt config activates the plugin's bundled custom `kode:` rule set
 * (e.g. `RouteWiringMethodNaming`) — top-level YAML key, anchored so it doesn't false-positive
 * on unrelated `kode`-containing text elsewhere in the file (e.g. `ru.kode.*` package names).
 */
internal fun activatesKodeRuleSet(configFile: File): Boolean {
    return KODE_RULE_SET_KEY_REGEX.containsMatchIn(configFile.readText())
}
