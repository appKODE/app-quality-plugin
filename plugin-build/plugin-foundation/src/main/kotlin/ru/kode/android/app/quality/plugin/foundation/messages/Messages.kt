@file:Suppress("TooManyFunctions") // Just simple strings providers

package ru.kode.android.app.quality.plugin.foundation.messages

import com.android.build.api.AndroidPluginVersion
import java.io.File

private val VERSION_NUMBER_REGEX = Regex("""\d+(?:\.\d+)+""")

/**
 * Error message shown when the plugin is applied to a non-Android application project.
 */
fun mustBeUsedWithAndroidMessage(): String =
    """
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
): String =
    """
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

/**
 * Error message shown when a resolved detekt config activates the plugin's bundled `kode:`
 * rule set but no dependency is wired into the matching `detekt.<platform>.rules` slot.
 */
fun missingKodeRuleSetDependencyMessage(
    platform: String,
    configFile: File,
): String =
    """
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
        |             from("ru.kode:detekt-rules:2.0.0")
        |             // from(libs.detekt.rules)  // or a catalog alias
        |         }
        |     }
        |============================================================
    """.trimMargin()

/**
 * Warning shown when a KODE rules jar checked in for AQP 2.x is still present.
 */
fun legacyRulesJarMessage(jar: File): String =
    """
        |
        |============================================================
        |          LEGACY KODE RULES JAR FOUND
        |============================================================
        | Since 3.0.0 the plugin resolves the 'kode' rule set from
        | Maven Central (ru.kode:detekt-rules) by default. This
        | checked-in jar is no longer needed:
        |
        |   ${jar.absolutePath}
        |
        | FIX: delete the jar and any detekt.*.rules {
        | from(files("libs/${jar.name}")) } entry pointing at it —
        | otherwise the 'kode' rules run twice. To keep the jar
        | instead, set detekt.android.rules.useDefaults = false.
        |============================================================
    """.trimMargin()

/**
 * Error message shown when `ru.kode.appQuality.detektEngine` holds an unsupported value.
 */
fun invalidDetektEngineMessage(value: String): String =
    """
        |
        |============================================================
        |              UNSUPPORTED DETEKT ENGINE
        |============================================================
        | Gradle property 'ru.kode.appQuality.detektEngine' is '$value'.
        | Supported values: 1 (detekt 1.23, default) or 2 (detekt 2).
        |
        | FIX: in gradle.properties set
        |
        |     ru.kode.appQuality.detektEngine=1
        |
        | or remove the property to use the default.
        |============================================================
    """.trimMargin()

/**
 * Error message shown when engine 2 is selected but the `dev.detekt` Gradle plugin is not on the
 * plugin classpath next to this plugin.
 */
fun detekt2PluginMissingMessage(version: String): String =
    """
        |
        |============================================================
        |           DETEKT 2 GRADLE PLUGIN NOT FOUND
        |============================================================
        | ru.kode.appQuality.detektEngine=2 needs the 'dev.detekt'
        | Gradle plugin on the same classpath as this plugin.
        |
        | FIX: declare it where the app-quality plugin is declared
        | (the root build script):
        |
        |     plugins {
        |         id("dev.detekt") version "$version" apply false
        |         id("ru.kode.android.app-quality.foundation") version "..."
        |     }
        |
        | If the app-quality plugin comes from buildSrc or a
        | convention-plugin build, add
        | "dev.detekt:detekt-gradle-plugin:$version" to that
        | build's dependencies instead.
        |============================================================
    """.trimMargin()

/**
 * Error message shown when engine 2 is selected but the `dev.detekt` Gradle plugin on the
 * classpath is not the version this plugin is built against ([actual] is `null` when unknown).
 */
fun detekt2VersionMismatchMessage(
    expected: String,
    actual: String?,
): String =
    """
        |
        |============================================================
        |           UNSUPPORTED DETEKT 2 GRADLE PLUGIN VERSION
        |============================================================
        | ru.kode.appQuality.detektEngine=2 is built against
        | 'dev.detekt' $expected, but the classpath has ${actual ?: "an unknown version"}.
        | detekt 2 is in alpha: its API changes between releases.
        |
        | FIX: declare exactly this version next to the plugin:
        |
        |     id("dev.detekt") version "$expected" apply false
        |============================================================
    """.trimMargin()

/**
 * Error message shown when engine 2 runs on a Kotlin Gradle plugin older than the one detekt 2
 * needs: it references KGP's `KotlinJvmExtension`, which KGP 2.0 lacks.
 */
fun detekt2KotlinPluginTooOldMessage(projectPath: String): String =
    """
        |
        |============================================================
        |        KOTLIN GRADLE PLUGIN TOO OLD FOR DETEKT 2
        |============================================================
        | Project '$projectPath' applies a Kotlin Gradle plugin older
        | than 2.1.21; ru.kode.appQuality.detektEngine=2 (detekt 2)
        | needs KGP 2.1.21 or newer.
        |
        | FIX: update the Kotlin Gradle plugin to 2.1.21+, or use
        | engine 1 (ru.kode.appQuality.detektEngine=1), which
        | supports KGP 2.0.21+.
        |============================================================
    """.trimMargin()

/**
 * Error message shown when a module applies the detekt plugin of the engine that is not selected.
 */
fun bothDetektPluginsMessage(
    projectPath: String,
    enginePluginId: String,
    otherPluginId: String,
): String =
    """
        |
        |============================================================
        |              TWO DETEKT PLUGINS IN ONE BUILD
        |============================================================
        | Project '$projectPath' applies '$otherPluginId', but the
        | app-quality plugin is configured for '$enginePluginId'.
        |
        | FIX — any one of:
        |  1. Remove id("$otherPluginId") from '$projectPath'; the
        |     app-quality plugin applies detekt itself
        |  2. Switch engines in gradle.properties:
        |     ru.kode.appQuality.detektEngine=${if (otherPluginId == "dev.detekt") 2 else 1}
        |============================================================
    """.trimMargin()

/**
 * Error message shown when an explicitly configured detekt config file does not exist.
 */
fun missingDetektConfigFileMessage(
    configFile: File,
    slot: String,
): String =
    """
        |
        |============================================================
        |              MISSING DETEKT CONFIG FILE
        |============================================================
        | '$slot' points at a file that does not exist:
        |
        |   ${configFile.absolutePath}
        |
        | FIX — any one of:
        |  1. Create the file at that path
        |  2. Fix the path in the root build script
        |  3. Remove the setting to use the bundled default config
        |============================================================
    """.trimMargin()

/**
 * Error message shown when `detekt.typeResolution` is on but an Android module has no variant
 * outside `detekt.ignoredBuildTypes` to analyse with type resolution.
 */
fun typeResolutionNothingToAnalyseMessage(
    projectPath: String,
    variants: Map<String, String>,
    ignoredBuildTypes: List<String>,
): String =
    """
        |
        |============================================================
        |          TYPE RESOLUTION: NOTHING TO ANALYSE
        |============================================================
        | Project '$projectPath' has no variant with a Kotlin
        | compilation and a detekt task to analyse with
        | detekt.typeResolution.
        |
        |   variants:          ${variants.entries.joinToString { "${it.key} (${it.value})" }.ifEmpty { "none" }}
        |   ignoredBuildTypes: $ignoredBuildTypes
        |
        | FIX — any one of:
        |  1. Remove a build type from detekt.ignoredBuildTypes
        |  2. Set detekt.typeResolution = false
        |============================================================
    """.trimMargin()

/**
 * Warning shown when `detekt.typeResolution` is on for a Kotlin Multiplatform module, which has
 * no single compilation to analyse: its plain detekt task runs without type resolution.
 */
fun typeResolutionMultiplatformMessage(projectPath: String): String =
    """
        |
        |============================================================
        |      TYPE RESOLUTION NOT SUPPORTED FOR MULTIPLATFORM
        |============================================================
        | Project '$projectPath' is a Kotlin Multiplatform module;
        | detekt runs on it without type resolution.
        |============================================================
    """.trimMargin()

/**
 * Error message shown when the editor configuration file is missing.
 */
fun noEditorConfigFileMessage(editorConfig: File): String =
    """
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
