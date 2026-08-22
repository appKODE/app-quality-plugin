package ru.kode.android.app.quality.plugin.foundation.config

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import ru.kode.android.gradle.commons.util.configureGroovy
import javax.inject.Inject

abstract class DetektConfig
    @Inject
    constructor(objectFactory: ObjectFactory) {
        @get:Nested
        val kotlin: PlatformDetektConfig =
            objectFactory.newInstance(PlatformDetektConfig::class.java)

        fun kotlin(action: Action<PlatformDetektConfig>) {
            action.execute(kotlin)
        }

        fun kotlin(
            @DelegatesTo(value = PlatformDetektConfig::class, strategy = Closure.DELEGATE_FIRST)
            closure: Closure<in PlatformDetektConfig>,
        ) {
            configureGroovy(closure, kotlin)
        }

        @get:Nested
        val android: PlatformDetektConfig =
            objectFactory.newInstance(PlatformDetektConfig::class.java)

        fun android(action: Action<PlatformDetektConfig>) {
            action.execute(android)
        }

        fun android(
            @DelegatesTo(value = PlatformDetektConfig::class, strategy = Closure.DELEGATE_FIRST)
            closure: Closure<in PlatformDetektConfig>,
        ) {
            configureGroovy(closure, android)
        }

        @get:Nested
        val compose: PlatformDetektConfig =
            objectFactory.newInstance(PlatformDetektConfig::class.java)

        fun compose(action: Action<PlatformDetektConfig>) {
            action.execute(compose)
        }

        fun compose(
            @DelegatesTo(value = PlatformDetektConfig::class, strategy = Closure.DELEGATE_FIRST)
            closure: Closure<in PlatformDetektConfig>,
        ) {
            configureGroovy(closure, compose)
        }

        val ignoredBuildTypes: ListProperty<String> =
            objectFactory.listProperty(String::class.java)
                .convention(listOf("release", "internal", "external", "demo"))

        /**
         * The detekt source-path slot: `include`/`exclude` globs (bare, no `!` prefix),
         * gated by `useDefaults`. Default = the plugin's bundled per-platform source paths.
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

        val typeResolution: Property<Boolean> =
            objectFactory.property(Boolean::class.java)
                .convention(false)

        /**
         * Optional detekt baseline file: findings already present in it are suppressed.
         * Unset by default (no baseline) — set to adopt detekt incrementally on a legacy
         * module, e.g. `baseline.set(layout.projectDirectory.file("detekt-baseline.xml"))`.
         *
         * Only the filename is used: it is re-resolved under each subproject's own directory,
         * so every subproject gets its own baseline file regardless of whether this is configured
         * once at the root (root-only application) or per-module (via a convention plugin).
         */
        val baseline: RegularFileProperty = objectFactory.fileProperty()

        /** Emit detekt's XML report per task. Default `false`. */
        val xmlReportEnabled: Property<Boolean> =
            objectFactory.property(Boolean::class.java)
                .convention(false)

        /** Emit detekt's SARIF report per task (e.g. for GitHub code scanning). Default `false`. */
        val sarifReportEnabled: Property<Boolean> =
            objectFactory.property(Boolean::class.java)
                .convention(false)
    }
