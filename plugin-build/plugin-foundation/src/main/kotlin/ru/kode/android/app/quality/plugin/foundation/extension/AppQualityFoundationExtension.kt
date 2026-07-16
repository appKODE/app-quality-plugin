package ru.kode.android.app.quality.plugin.foundation.extension

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import ru.kode.android.app.quality.plugin.foundation.config.AndroidLintConfig
import ru.kode.android.app.quality.plugin.foundation.config.DetektConfig
import ru.kode.android.app.quality.plugin.foundation.config.KtlintConfig
import ru.kode.android.build.publish.plugin.core.api.extension.BuildPublishConfigurableExtension
import ru.kode.android.build.publish.plugin.core.util.configureGroovy
import javax.inject.Inject

@Suppress("UnnecessaryAbstractClass")
abstract class AppQualityFoundationExtension
    @Inject
    constructor(objectFactory: ObjectFactory) : BuildPublishConfigurableExtension() {
        /**
         * Enables verbose logging for the build and publish plugins.
         *
         * If set to `true`, the plugin will print more detailed logs during the build process.
         *
         * Default value is `false`.
         */
        val verboseLogging: Property<Boolean> =
            objectFactory.property(Boolean::class.java)
                .convention(false)

        val jvmTarget: Property<JvmTarget> =
            objectFactory.property(JvmTarget::class.java)
                .convention(JvmTarget.JVM_17)

        val gitHooks: RegularFileProperty = objectFactory.fileProperty()

        /**
         * Whether the plugin should point `git config core.hooksPath` at [gitHooks].
         *
         * Set to `false` to opt out of git hooks setup entirely, e.g. when the consuming
         * project manages `core.hooksPath` itself. Default value is `true`.
         */
        val gitHooksEnabled: Property<Boolean> =
            objectFactory.property(Boolean::class.java)
                .convention(true)

        @get:Nested
        val ktlint: KtlintConfig =
            objectFactory.newInstance(KtlintConfig::class.java)

        fun ktlint(action: Action<KtlintConfig>) {
            action.execute(ktlint)
        }

        fun ktlint(
            @DelegatesTo(value = KtlintConfig::class, strategy = Closure.DELEGATE_FIRST)
            closure: Closure<in KtlintConfig>,
        ) {
            configureGroovy(closure, ktlint)
        }

        @get:Nested
        val detekt: DetektConfig =
            objectFactory.newInstance(DetektConfig::class.java)

        fun detekt(action: Action<DetektConfig>) {
            action.execute(detekt)
        }

        fun detekt(
            @DelegatesTo(value = DetektConfig::class, strategy = Closure.DELEGATE_FIRST)
            closure: Closure<in DetektConfig>,
        ) {
            configureGroovy(closure, detekt)
        }

        @get:Nested
        val androidLint: AndroidLintConfig =
            objectFactory.newInstance(AndroidLintConfig::class.java)

        fun androidLint(action: Action<AndroidLintConfig>) {
            action.execute(androidLint)
        }

        fun androidLint(
            @DelegatesTo(value = AndroidLintConfig::class, strategy = Closure.DELEGATE_FIRST)
            closure: Closure<in AndroidLintConfig>,
        ) {
            configureGroovy(closure, androidLint)
        }
    }
