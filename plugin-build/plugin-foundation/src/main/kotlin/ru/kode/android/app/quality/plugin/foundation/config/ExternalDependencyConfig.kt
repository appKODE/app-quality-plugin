package ru.kode.android.app.quality.plugin.foundation.config

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

/**
 * A dependency slot configurable from any source: version-catalog accessors, string
 * coordinates, jar files, or Dependency objects. [from] is Gradle's [DependencyCollector],
 * so all of `from("group:name:version")`, `from(libs.alias)` and `from(files("x.jar"))`
 * work inside the configuration block.
 *
 * The plugin's default for the slot is independent of user additions and controlled only
 * by [useDefaults] — additions always stack on top of the default unless it is disabled.
 */
abstract class ExternalDependencyConfig
    @Inject
    constructor(objectFactory: ObjectFactory) {
        /**
         * User-added dependency sources (add-only; all source kinds).
         * Constraints can be added via `from.addConstraint(...)`.
         */
        abstract val from: DependencyCollector

        /** Include the plugin's defaults for this slot. Default `true`. */
        val useDefaults: Property<Boolean> =
            objectFactory.property(Boolean::class.java)
                .convention(true)

        // MUST stay a concrete internal val (NOT an abstract managed property): Kotlin mangles
        // internal member JVM names, which breaks the class generator's property recognition.
        // Catalog/coordinate-based defaults only (ExternalModuleDependency-shaped values) —
        // file-based defaults go through [defaultFiles] instead, see its doc for why.
        internal val defaults: ListProperty<Dependency> =
            objectFactory.listProperty(Dependency::class.java)

        /**
         * Plugin-seeded file-based defaults (e.g. a bundled rules jar materialized from a
         * plugin resource) — kept as a SEPARATE [DependencyCollector] from [defaults], not a
         * `ListProperty<Dependency>` entry: a raw [org.gradle.api.artifacts.FileCollectionDependency]
         * stored in a plain `ListProperty` fails Gradle's configuration-cache serialization
         * ("cannot serialize DefaultFileCollectionDependency"), empirically confirmed — whereas
         * `DependencyCollector.add(FileCollection)` (the real method backing [from]'s
         * `from(files(...))` DSL sugar) has native, working config-cache support. Not part of
         * the public DSL surface; the plugin seeds it via `.add(...)` directly, never the user.
         */
        abstract val defaultFiles: DependencyCollector
    }

/**
 * Whether the user has added anything to this slot explicitly, as a lazy [Provider] — not an
 * eager `Boolean`, so callers can compose it into a Provider chain without capturing this
 * [ExternalDependencyConfig] instance directly inside a closure (see the config-cache note on
 * `noKodeRuleSetJarWiredAnywhereProvider` in `AppQualityFoundationPlugin.kt` for why that
 * distinction matters). Deliberately ignores the slot's baked-in plugin default
 * (`detekt-formatting`, `detekt-compose-rules`): those are known never to satisfy a custom rule
 * set like `kode`, so counting them here would silently defeat a "did you forget to add the
 * rules jar" check for any slot that has one.
 */
internal fun ExternalDependencyConfig.hasNoUserAdditionsProvider(): Provider<Boolean> {
    return from.dependencies.map { it.isEmpty() }
}
