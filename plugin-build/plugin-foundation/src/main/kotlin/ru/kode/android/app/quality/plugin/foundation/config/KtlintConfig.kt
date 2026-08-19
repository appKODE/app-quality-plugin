package ru.kode.android.app.quality.plugin.foundation.config

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import ru.kode.android.gradle.commons.util.configureGroovy
import javax.inject.Inject

abstract class KtlintConfig
    @Inject
    constructor(objectFactory: ObjectFactory) {
        val projectConfig: RegularFileProperty = objectFactory.fileProperty()

        /**
         * The ktlint CLI classpath slot. Default = the `ktlint-cli` alias of the `libs`
         * version catalog; configure from any source:
         * `cli { from("com.pinterest.ktlint:ktlint-cli:1.8.0"); useDefaults.set(false) }`.
         */
        val cli: ExternalDependencyConfig =
            objectFactory.newInstance(ExternalDependencyConfig::class.java)

        fun cli(action: Action<ExternalDependencyConfig>) {
            action.execute(cli)
        }

        fun cli(
            @DelegatesTo(value = ExternalDependencyConfig::class, strategy = Closure.DELEGATE_FIRST)
            closure: Closure<in ExternalDependencyConfig>,
        ) {
            configureGroovy(closure, cli)
        }

        /**
         * The Kotlin source-pattern slot: `include`/`exclude` globs (bare, no `!` prefix),
         * gated by `useDefaults`. Default = the plugin's bundled Kotlin source/ignore globs.
         */
        val sources: SourcePatternsConfig =
            objectFactory.newInstance(SourcePatternsConfig::class.java)

        fun sources(action: Action<SourcePatternsConfig>) {
            action.execute(sources)
        }

        fun sources(
            @DelegatesTo(value = SourcePatternsConfig::class, strategy = Closure.DELEGATE_FIRST)
            closure: Closure<in SourcePatternsConfig>,
        ) {
            configureGroovy(closure, sources)
        }
    }
