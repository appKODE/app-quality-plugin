package ru.kode.android.app.quality.plugin.foundation.config

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import javax.inject.Inject

abstract class DetektConfig
    @Inject
    constructor(objectFactory: ObjectFactory) {
        @get:Nested
        val kotlin: PlatformDetektConfig =
            objectFactory.newInstance(PlatformDetektConfig::class.java)

        @get:Nested
        val android: PlatformDetektConfig =
            objectFactory.newInstance(PlatformDetektConfig::class.java)

        @get:Nested
        val compose: PlatformDetektConfig =
            objectFactory.newInstance(PlatformDetektConfig::class.java)

        val ignoredBuildTypes: ListProperty<String> =
            objectFactory.listProperty(String::class.java)
                .convention(listOf("release", "internal", "external", "demo"))

        val additionallyExcludedPaths: ListProperty<String> =
            objectFactory.listProperty(String::class.java)
                .convention(emptyList())

        val additionalSourcePaths: ListProperty<String> =
            objectFactory.listProperty(String::class.java)
                .convention(emptyList())

        val typeResolution: Property<Boolean> =
            objectFactory.property(Boolean::class.java)
                .convention(false)
    }
