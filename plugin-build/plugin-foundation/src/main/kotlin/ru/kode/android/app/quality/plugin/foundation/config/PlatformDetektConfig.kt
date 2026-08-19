package ru.kode.android.app.quality.plugin.foundation.config

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import ru.kode.android.gradle.commons.util.configureGroovy
import javax.inject.Inject

abstract class PlatformDetektConfig
    @Inject
    constructor(objectFactory: ObjectFactory) {
        val projectConfig: RegularFileProperty = objectFactory.fileProperty()

        /**
         * This platform's detekt rule-set slot, added to `detektPlugins` of matching modules.
         * Defaults per platform (`detekt-formatting` for kotlin, `detekt-compose-rules` for
         * compose); configure from any source:
         * `rules { from(files("libs/my-rules.jar")) }` — additions stack ON TOP of the
         * default unless `useDefaults.set(false)`.
         */
        val rules: ExternalDependencyConfig =
            objectFactory.newInstance(ExternalDependencyConfig::class.java)

        fun rules(action: Action<ExternalDependencyConfig>) {
            action.execute(rules)
        }

        fun rules(
            @DelegatesTo(value = ExternalDependencyConfig::class, strategy = Closure.DELEGATE_FIRST)
            closure: Closure<in ExternalDependencyConfig>,
        ) {
            configureGroovy(closure, rules)
        }
    }
