@file:Suppress("TooManyFunctions") // Just simple strings providers

package ru.kode.android.app.quality.plugin.foundation.messages

import com.android.build.api.AndroidPluginVersion
import java.io.File

private val VERSION_NUMBER_REGEX = Regex("""\d+(?:\.\d+)+""")

/**
 * Error message shown when the plugin is applied to a non-Android application project.
 */
fun mustBeUsedWithAndroidMessage(): String {
    return """
        |
        |============================================================
        |                 PLUGIN CONFIGURATION ERROR   
        |============================================================
        | This plugin can only be used with Android application 
        | projects.
        |
        | REQUIRED ACTION:
        |  1. Apply the 'com.android.application' plugin in 
        |     your build.gradle.kts:
        |
        | plugins {
        |     id("com.android.application")
        |     // Other plugins...
        | }
        |
        | NOTE: This plugin is not compatible with library projects.
        |============================================================
        """.trimMargin()
}

/**
 * Error message shown when the Android Gradle Plugin version is below the required minimum.
 */
fun mustBeUsedWithVersionMessage(version: AndroidPluginVersion): String {
    val versionNumber = VERSION_NUMBER_REGEX.find(version.toString())?.value
    return """
        |
        |============================================================
        |         UNSUPPORTED ANDROID GRADLE PLUGIN VERSION   
        |============================================================
        | This plugin requires Android Gradle Plugin version $versionNumber 
        | or higher.
        |
        | REQUIRED ACTION:
        |  1. Update your project's build.gradle.kts to use AGP $versionNumber 
        |     or later:
        |
        |  plugins {
        |      id("com.android.application") version "$versionNumber"
        |      // Or a newer version
        |  }
        |
        |  2. Sync your project with Gradle files
        |  3. Clean and rebuild your project
        |============================================================
        """.trimMargin()
}

/**
 * Error message shown when the required dependency is not found in the version catalog.
 */
fun noKtlintDependencyReferenceInLibsMessage(name: String): String {
    return """
        |
        |============================================================
        |          MISSING KTLINT DEPENDENCY IN VERSION CATALOG   
        |============================================================
        | The required '$name' dependency is not defined in 
        | your libs.versions.toml file.
        |
        | REQUIRED ACTION:
        |  1. Add the following entries to your version catalog:
        |
        |  [versions]
        |  $name = "0.50.0"
        |
        |  [libraries]
        |  $name = { module = "com.pinterest:ktlint", version.ref = "$name" }
        |
        |  2. Sync your project with Gradle files
        |============================================================
        """.trimMargin()
}

fun noDetektRulesDependencyReferenceInLibsMessage(name: String): String {
    return """
        |
        |============================================================
        |          MISSING DETEKT DEPENDENCY IN VERSION CATALOG   
        |============================================================
        | The required '$name' dependency is not defined in 
        | your libs.versions.toml file.
        |
        | REQUIRED ACTION:
        |  1. Add the following entries to your version catalog:
        |
        |  [versions]
        |  detektLibName = "1.4.0" // replace with real name and version and use in ref value
        |
        |  [libraries]
        |  $name = { module = "ru.kode:detekt-rules-compose", version.ref = "detektLibName" }
        |
        |  2. Sync your project with Gradle files
        |============================================================
        """.trimMargin()
}

/**
 * Error message shown when the editor configuration file is missing.
 */
fun noEditorConfigFileMessage(editorConfig: File): String {
    return """
        |
        |============================================================
        |                MISSING CONFIGURATION FILE   
        |============================================================
        | The required configuration file '${editorConfig.name}' 
        | was not found in the project root.
        |
        | REQUIRED ACTION:
        |  1. Create a '${editorConfig.name}' file in your project 
        |     root directory.
        |  2. Configure your quality rules in this file.
        |============================================================
        """.trimMargin()
}
