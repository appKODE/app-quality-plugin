package ru.kode.android.app.quality.plugin.foundation.extension

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import ru.kode.android.app.quality.plugin.foundation.config.DetektConfig
import ru.kode.android.app.quality.plugin.foundation.config.KtlintConfig
import ru.kode.android.build.publish.plugin.core.api.extension.BuildPublishConfigurableExtension
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

        val versionCatalogName: Property<String> =
            objectFactory.property(String::class.java)
                .convention("libs")

        @get:Nested
        val ktlint: KtlintConfig =
            objectFactory.newInstance(KtlintConfig::class.java)

        @get:Nested
        val detekt: DetektConfig =
            objectFactory.newInstance(DetektConfig::class.java)
    }
