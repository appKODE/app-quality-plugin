package ru.kode.android.app.quality.plugin.foundation.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Points `git config core.hooksPath` at the configured hooks directory.
 *
 * Skipped when the root project is not a git repository (fresh CI checkouts, source archives).
 */
@UntrackedTask(because = "Mutates local git configuration, which is not a build output")
abstract class GitHooksSetupTask : DefaultTask() {
    @get:Input
    abstract val hooksPath: Property<String>

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        description = "Changes git core.hooksPath to the project hooks directory"
        onlyIf("root project is a git repository") { task ->
            (task as GitHooksSetupTask).rootDir.get().asFile.resolve(".git").exists()
        }
    }

    @TaskAction
    fun setup() {
        execOperations.exec { spec ->
            spec.workingDir(rootDir.get().asFile)
            spec.commandLine("git", "config", "core.hooksPath", hooksPath.get())
        }
    }
}
