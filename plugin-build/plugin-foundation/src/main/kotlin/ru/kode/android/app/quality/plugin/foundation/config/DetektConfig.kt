package ru.kode.android.app.quality.plugin.foundation.config

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
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
                .convention(listOf("release"))

        val additionallyExcludedPaths: ListProperty<String> =
            objectFactory.listProperty(String::class.java)
                .convention(emptyList())
    }
