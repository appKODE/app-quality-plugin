package ru.kode.android.app.quality.plugin.test.utils

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import java.io.File

private val IS_CI get() = System.getenv("CI") == "true"

const val DEFAULT_GRADLE_VERSION = "9.4.1"

fun File.getFile(path: String): File {
    val file = File(this, path)
    file.parentFile.mkdirs()
    return file
}

@Suppress("LongParameterList")
fun File.runTasks(
    vararg tasks: String,
    arguments: List<String> = emptyList(),
    taskArguments: Map<String, String> = emptyMap(),
    agpClasspath: List<File> = emptyList(),
    gradleVersion: String = DEFAULT_GRADLE_VERSION,
    gradleJvmArgs: List<String> = emptyList(),
    expectFailure: Boolean = false,
): BuildResult {
    val args =
        tasks.toMutableList().apply {
            if (!IS_CI) add("--info")
            add("--stacktrace")
            addAll(arguments)
            taskArguments.forEach { (key, value) ->
                add("-D$key=$value")
            }
        }
    val env =
        System.getenv().toMutableMap().apply {
            if (gradleJvmArgs.isNotEmpty()) {
                this["GRADLE_OPTS"] = gradleJvmArgs.joinToString(" ")
            }
        }
    val runner =
        GradleRunner.create()
            .withProjectDir(this)
            .withArguments(args)
            .withEnvironment(env)
            .apply {
                if (agpClasspath.isNotEmpty()) {
                    withPluginClasspath(prepareClasspath(agpClasspath))
                } else {
                    withPluginClasspath()
                }
            }
            .withGradleVersion(gradleVersion)
            .forwardOutput()
    return if (expectFailure) runner.buildAndFail() else runner.build()
}

fun File.runTask(
    task: String,
    arguments: List<String> = emptyList(),
    gradleVersion: String = DEFAULT_GRADLE_VERSION,
): BuildResult {
    return runTasks(task, arguments = arguments, gradleVersion = gradleVersion)
}

fun File.runTaskWithFail(
    task: String,
    arguments: List<String> = emptyList(),
    gradleVersion: String = DEFAULT_GRADLE_VERSION,
): BuildResult {
    return runTasks(task, arguments = arguments, gradleVersion = gradleVersion, expectFailure = true)
}

private fun prepareClasspath(agpClassPath: List<File>): List<File> {
    val pluginClasspath: List<File> = PluginUnderTestMetadataReading.readImplementationClasspath()
    // Drop the default AGP artifacts (resolved from the `com.android.*` groups) so the
    // injected AGP version fully replaces them instead of clashing on the classpath.
    val filteredClasspath =
        pluginClasspath.filter { file ->
            !file.path.replace('\\', '/').contains("/com.android")
        }
    val dropped = pluginClasspath.size - filteredClasspath.size
    println("Dropped $dropped default AGP jars, adding ${agpClassPath.size} AGP jars")
    return filteredClasspath + agpClassPath
}

/**
 * Resolves arbitrary dependency notations (with transitives) through a throwaway project —
 * used to obtain tool jars for classpath-injection and file-based configuration tests.
 */
fun resolveJars(vararg notations: String): List<File> {
    val project =
        ProjectBuilder.builder()
            .withName("temp-resolver")
            .build()

    project.buildscript.repositories.apply {
        google()
        mavenCentral()
    }

    val resolved =
        project.buildscript.configurations.getByName("classpath").apply {
            dependencies.clear()
            notations.forEach { notation ->
                dependencies.add(project.dependencies.create(notation))
            }
        }.resolve()

    return resolved.toList()
}

fun resolveRequiredAgpJars(agpVersion: String): List<File> {
    val agpPluginMarker = "com.android.application:com.android.application.gradle.plugin"
    return resolveJars(
        "com.android.tools.build:gradle:$agpVersion",
        "$agpPluginMarker:$agpVersion",
    )
}
