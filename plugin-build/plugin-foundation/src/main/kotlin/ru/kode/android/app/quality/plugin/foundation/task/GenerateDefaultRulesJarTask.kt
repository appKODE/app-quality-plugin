package ru.kode.android.app.quality.plugin.foundation.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.Base64

/**
 * Materializes a bundled default rules jar (binary) into the build directory, so it can be
 * used when a project hasn't configured its own dependency for the slot.
 *
 * Content travels as Base64 through a [Property]<String> — the same content-is-the-input,
 * config-cache-safe shape [GenerateDefaultConfigFileTask] uses for text resources, without
 * relying on [ByteArray] property snapshotting.
 */
@CacheableTask
abstract class GenerateDefaultRulesJarTask : DefaultTask() {
    @get:Input
    abstract val resourceContentBase64: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        outputFile.get().asFile.writeBytes(Base64.getDecoder().decode(resourceContentBase64.get()))
    }
}
