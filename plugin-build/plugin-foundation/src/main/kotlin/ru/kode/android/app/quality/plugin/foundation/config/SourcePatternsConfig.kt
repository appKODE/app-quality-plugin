package ru.kode.android.app.quality.plugin.foundation.config

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * A source-pattern slot: add-only `include`/`exclude` glob lists, gated by [useDefaults].
 * Patterns are bare (no `!` prefix) — consumers that need the ktlint-CLI ignore syntax add
 * the prefix themselves when building CLI args.
 */
abstract class SourcePatternsConfig
    @Inject
    constructor(objectFactory: ObjectFactory) {
        /** User-added include patterns (add-only). */
        val include: ListProperty<String> =
            objectFactory.listProperty(String::class.java)
                .convention(emptyList())

        /** User-added exclude patterns (add-only, bare — no `!` prefix). */
        val exclude: ListProperty<String> =
            objectFactory.listProperty(String::class.java)
                .convention(emptyList())

        /** Include the plugin's default patterns for this slot. Default `true`. */
        val useDefaults: Property<Boolean> =
            objectFactory.property(Boolean::class.java)
                .convention(true)

        /** DSL sugar for `include.addAll(...)`, e.g. `include("a", "b")`. */
        fun include(vararg patterns: String) {
            include.addAll(*patterns)
        }

        /** DSL sugar for `exclude.addAll(...)`, e.g. `exclude("a", "b")`. */
        fun exclude(vararg patterns: String) {
            exclude.addAll(*patterns)
        }
    }
