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
 * Error message shown when a file configured in a dependency slot does not exist on disk.
 * [slot] is the fully qualified slot path, e.g. `detekt.kotlin.rules` or `ktlint.cli` — it is
 * the only locus the user gets, since the failure surfaces from dependency resolution.
 */
fun missingDependencyFileMessage(
    file: File,
    slot: String,
): String {
    return """
        |
        |============================================================
        |             MISSING DEPENDENCY FILE
        |============================================================
        | A file configured in the '$slot' slot does not exist:
        |
        |   ${file.absolutePath}
        |
        | FIX — any one of:
        |  1. Put the jar at that path
        |  2. Fix the path in the root build script:
        |
        |     appQualityFoundation {
        |         $slot {
        |             from(files("path/to/your.jar"))
        |         }
        |     }
        |
        |  3. Remove the entry if it is not needed
        |============================================================
        """.trimMargin()
}

/**
 * Error message shown when a resolved detekt config activates the plugin's bundled `kode:`
 * rule set but no dependency is wired into the matching `detekt.<platform>.rules` slot.
 */
fun missingKodeRuleSetDependencyMessage(
    platform: String,
    configFile: File,
): String {
    return """
        |
        |============================================================
        |          MISSING DEPENDENCY FOR 'kode' RULE SET
        |============================================================
        | The detekt config activates the plugin's custom 'kode'
        | rule set (e.g. RouteWiringMethodNaming), but no dependency
        | is wired into 'detekt.$platform.rules':
        |
        |   ${configFile.absolutePath}
        |
        | FIX — configure the slot in the root build script:
        |
        |     appQualityFoundation {
        |         detekt.$platform.rules {
        |             from(files("libs/detekt-rules-1.4.0.jar")) // checked-in jar
        |             // from(yourCatalog.detekt.rulesCompose)   // or a catalog alias
        |         }
        |     }
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
        | The ktlint configuration file was not found:
        |
        |   ${editorConfig.absolutePath}
        |
        | FIX — any one of:
        |  1. Create '${editorConfig.name}' at that path and configure
        |     your formatting rules in it
        |  2. Point the plugin at an existing file in the root
        |     build script:
        |
        |     appQualityFoundation {
        |         ktlint.projectConfig.set(rootProject.layout.projectDirectory.file("config/.editorconfig"))
        |     }
        |============================================================
        """.trimMargin()
}
