package ru.kode.android.app.quality.plugin.foundation.config

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class KtlintConfig
    @Inject
    constructor(objectFactory: ObjectFactory) {

        val projectConfig: RegularFileProperty = objectFactory.fileProperty()

        val cliLibraryName: Property<String> =
            objectFactory.property(String::class.java)
                .convention("ktlint.cli")

        val additionalSourcePatterns: ListProperty<String> =
            objectFactory.listProperty(String::class.java)
                .convention(emptyList())

        val additionalIgnoredSourcePatterns: ListProperty<String> =
            objectFactory.listProperty(String::class.java)
                .convention(emptyList())
    }
