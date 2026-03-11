package ru.kode.android.app.quality.plugin.foundation.config

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import javax.inject.Inject

abstract class PlatformDetektConfig
    @Inject
    constructor(objectFactory: ObjectFactory) {
        val projectConfig: RegularFileProperty = objectFactory.fileProperty()

        val rulesLibraryNames: ListProperty<String> =
            objectFactory.listProperty(String::class.java)

        val rulesPluginJar: RegularFileProperty = objectFactory.fileProperty()

    }
