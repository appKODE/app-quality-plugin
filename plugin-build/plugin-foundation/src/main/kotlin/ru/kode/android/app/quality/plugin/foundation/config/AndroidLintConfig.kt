package ru.kode.android.app.quality.plugin.foundation.config

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Opt-in wiring for the Android Gradle Plugin's own `lint` task into the aggregate
 * `pipelineCheck`/`prePushCheck` tasks. Off by default: lint is slow and most consumers
 * already run it separately in CI.
 */
abstract class AndroidLintConfig
    @Inject
    constructor(objectFactory: ObjectFactory) {
        val enabled: Property<Boolean> =
            objectFactory.property(Boolean::class.java)
                .convention(false)
    }
