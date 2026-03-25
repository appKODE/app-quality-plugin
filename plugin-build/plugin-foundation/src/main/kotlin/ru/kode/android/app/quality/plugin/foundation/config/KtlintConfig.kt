package ru.kode.android.app.quality.plugin.foundation.config

import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class KtlintConfig
    @Inject
    constructor(objectFactory: ObjectFactory) {
        val projectConfig: RegularFileProperty = objectFactory.fileProperty()

        val cliLibrary: Property<MinimalExternalModuleDependency> =
            objectFactory.property(MinimalExternalModuleDependency::class.java)

        val additionalSourcePatterns: ListProperty<String> =
            objectFactory.listProperty(String::class.java)
                .convention(emptyList())

        val additionalIgnoredSourcePatterns: ListProperty<String> =
            objectFactory.listProperty(String::class.java)
                .convention(emptyList())
    }
