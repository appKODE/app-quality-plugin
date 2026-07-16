package ru.kode.android.app.quality.plugin.foundation.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Materializes a bundled default config file (detekt config, ktlint editorconfig)
 * into the build directory, so it can be used when a project-level file is absent.
 *
 * The content is an input: upgrading the plugin (new bundled defaults) reruns the task.
 */
@CacheableTask
abstract class GenerateDefaultConfigFileTask : DefaultTask() {
    @get:Input
    abstract val resourceContent: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        outputFile.get().asFile.writeText(resourceContent.get())
    }
}
