package ru.kode.android.app.quality.plugin.test.utils

import java.io.File

/**
 * Module archetypes supported by the generated test project.
 */
enum class ModuleType {
    AndroidApp,
    AndroidLib,
    KotlinJvm,
    JavaOnly,
}

/**
 * Mirrors an `ExternalDependencyConfig` slot: all source kinds plus the defaults toggle.
 * [refs] are typed catalog accessor expressions (e.g. "deps.ktlint.cli"), [notations] are
 * string coordinates, [files] are root-relative jar paths.
 */
data class DependencySlot(
    val refs: List<String>? = null,
    val notations: List<String>? = null,
    val files: List<String>? = null,
    val useDefaults: Boolean? = null,
)

/**
 * Mirrors a `SourcePatternsConfig` slot: add-only `include`/`exclude` globs (bare, no `!`
 * prefix) plus the defaults toggle.
 */
data class SourcePatternsSlot(
    val include: List<String>? = null,
    val exclude: List<String>? = null,
    val useDefaults: Boolean? = null,
)

/**
 * Mirrors the `appQualityFoundation { ktlint { ... } }` block. All paths are relative to the project root.
 */
data class KtlintBlock(
    val projectConfigPath: String? = null,
    val sources: SourcePatternsSlot? = null,
    val cli: DependencySlot? = null,
)

/**
 * Mirrors `appQualityFoundation { detekt { kotlin/android/compose { ... } } }` platform blocks.
 */
data class PlatformDetektBlock(
    val projectConfigPath: String? = null,
    val rules: DependencySlot? = null,
)

/**
 * Mirrors the `appQualityFoundation { detekt { ... } }` block.
 */
data class DetektBlock(
    val ignoredBuildTypes: List<String>? = null,
    val sources: SourcePatternsSlot? = null,
    val typeResolution: Boolean? = null,
    val kotlin: PlatformDetektBlock? = null,
    val android: PlatformDetektBlock? = null,
    val compose: PlatformDetektBlock? = null,
)

/**
 * Mirrors the whole `appQualityFoundation { ... }` extension. Null values are omitted from
 * the generated build script, so the plugin's own defaults/conventions apply.
 */
data class QualityConfig(
    val verboseLogging: Boolean? = null,
    val jvmTarget: String? = null,
    val gitHooksPath: String? = null,
    val gitHooksEnabled: Boolean? = null,
    val ktlint: KtlintBlock? = null,
    val detekt: DetektBlock? = null,
    /** Raw Groovy lines appended verbatim inside the appQualityFoundation block. */
    val extraExtensionContent: String? = null,
)

/**
 * One module of the generated multi-module project.
 *
 * [buildTypes] lists ADDITIONAL android build types (debug/release always exist).
 * [kotlinSources]/[javaSources] map module-relative paths to file content.
 */
data class ModuleSpec(
    val name: String,
    val type: ModuleType,
    val buildTypes: List<String> = emptyList(),
    val detektKotlinConfigContent: String? = null,
    val detektAndroidConfigContent: String? = null,
    val detektComposeConfigContent: String? = null,
    val kotlinSources: Map<String, String> = emptyMap(),
    val javaSources: Map<String, String> = emptyMap(),
    val compileSdk: Int = 36,
    // Applies org.jetbrains.kotlin.android explicitly — the pre-AGP-9 setup where detekt
    // registers variant tasks (detektDebug, detektRelease). Use with an injected AGP 8.x.
    val applyKotlinAndroidPlugin: Boolean = false,
    // Applies org.jetbrains.kotlin.plugin.compose so the plugin's compose detekt layer kicks in.
    val applyComposePlugin: Boolean = false,
    // Applies org.jetbrains.compose (JetBrains Compose Multiplatform) alongside
    // org.jetbrains.kotlin.plugin.compose, which it requires since Compose Multiplatform
    // 1.6.10. The plugin's compose detekt layer triggers on either compose plugin id.
    val applyJetbrainsComposePlugin: Boolean = false,
    // Applies org.jetbrains.kotlin.multiplatform with a minimal `kotlin { jvm() }` target.
    val applyMultiplatformPlugin: Boolean = false,
)

/**
 * Controls the generated version catalog. With the default [name] "libs" the toml is written
 * to gradle/libs.versions.toml (Gradle auto-loads it); a custom name writes
 * gradle/<name>.versions.toml plus an explicit versionCatalogs declaration — and NO `libs`
 * catalog exists at all, which is what the decoupling tests need. Omitting aliases enables
 * negative tests of the error messages; [generate] = false produces a project with no catalog.
 */
data class LibsCatalog(
    val name: String = "libs",
    val generate: Boolean = true,
    val includeKtlintCli: Boolean = true,
    val includeDetektFormatting: Boolean = true,
    val includeDetektComposeRules: Boolean = true,
)

/**
 * Generates a multi-module Gradle project applying `ru.kode.android.app-quality.foundation`
 * at the ROOT project (the plugin's real usage mode) with the given extension configuration.
 */
@Suppress("LongParameterList")
fun File.createQualityProject(
    modules: List<ModuleSpec>,
    qualityConfig: QualityConfig = QualityConfig(),
    rootEditorConfigContent: String? = null,
    extraRootFiles: Map<String, String> = emptyMap(),
    rulesJar: File? = null,
    libsCatalog: LibsCatalog = LibsCatalog(),
    gradleProperties: Map<String, String> = emptyMap(),
    useKotlinDsl: Boolean = false,
) {
    val settingsFileName = if (useKotlinDsl) "settings.gradle.kts" else "settings.gradle"
    val buildFileName = if (useKotlinDsl) "build.gradle.kts" else "build.gradle"
    writeFileVerbose(getFile(settingsFileName), settingsFileContent(modules, libsCatalog, useKotlinDsl))
    writeFileVerbose(getFile(buildFileName), rootBuildFileContent(qualityConfig, useKotlinDsl))
    if (libsCatalog.generate) {
        writeFileVerbose(getFile(libsCatalog.tomlPath()), libsCatalogContent(libsCatalog))
    }

    val properties = mapOf("org.gradle.jvmargs" to "-Xmx2g") + gradleProperties
    writeFileVerbose(
        getFile("gradle.properties"),
        properties.entries.joinToString("\n") { (key, value) -> "$key=$value" },
    )

    androidSdkPath()?.let { sdk ->
        writeFileVerbose(getFile("local.properties"), "sdk.dir=$sdk")
    }

    rootEditorConfigContent?.let { writeFileVerbose(getFile(".editorconfig"), it) }
    extraRootFiles.forEach { (path, content) -> writeFileVerbose(getFile(path), content) }
    rulesJar?.let { jar -> jar.copyTo(getFile("libs/detekt-rules-1.4.0.jar"), overwrite = true) }

    modules.forEach { module -> writeModule(module, useKotlinDsl) }
}

private fun File.writeModule(
    module: ModuleSpec,
    useKotlinDsl: Boolean,
) {
    val moduleDir = File(this, module.name)
    val buildFileName = if (useKotlinDsl) "build.gradle.kts" else "build.gradle"
    writeFileVerbose(moduleDir.getFile(buildFileName), moduleBuildFileContent(module, useKotlinDsl))

    if (module.type == ModuleType.AndroidApp || module.type == ModuleType.AndroidLib) {
        writeFileVerbose(
            moduleDir.getFile("src/main/AndroidManifest.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:label="Test App" />
            </manifest>
            """.trimIndent(),
        )
    }

    module.detektKotlinConfigContent?.let {
        writeFileVerbose(moduleDir.getFile("detekt-kotlin-config.yml"), it)
    }
    module.detektAndroidConfigContent?.let {
        writeFileVerbose(moduleDir.getFile("detekt-android-config.yml"), it)
    }
    module.detektComposeConfigContent?.let {
        writeFileVerbose(moduleDir.getFile("detekt-compose-config.yml"), it)
    }
    module.kotlinSources.forEach { (path, content) ->
        writeFileVerbose(moduleDir.getFile(path), content)
    }
    module.javaSources.forEach { (path, content) ->
        writeFileVerbose(moduleDir.getFile(path), content)
    }
}

private fun LibsCatalog.tomlPath(): String {
    return if (name == "libs") "gradle/libs.versions.toml" else "gradle/$name.versions.toml"
}

private fun settingsFileContent(
    modules: List<ModuleSpec>,
    libsCatalog: LibsCatalog,
    useKotlinDsl: Boolean,
): String {
    val includes =
        modules.joinToString("\n") {
            if (useKotlinDsl) "include(\":${it.name}\")" else "include ':${it.name}'"
        }
    // gradle/libs.versions.toml is auto-loaded as `libs`; a custom name needs a declaration.
    val versionCatalogsBlock =
        if (libsCatalog.generate && libsCatalog.name != "libs") {
            if (useKotlinDsl) {
                """
                versionCatalogs {
                    create("${libsCatalog.name}") {
                        from(files("${libsCatalog.tomlPath()}"))
                    }
                }
                """
            } else {
                """
                versionCatalogs {
                    create('${libsCatalog.name}') {
                        from(files('${libsCatalog.tomlPath()}'))
                    }
                }
                """
            }
        } else {
            ""
        }
    return """
        pluginManagement {
            repositories {
                mavenLocal()
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }

        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                mavenLocal()
                google()
                mavenCentral()
            }
            $versionCatalogsBlock
        }

        rootProject.name = "quality-test-project"
        $includes
        """.trimIndent().removeBlankLines()
}

private fun rootBuildFileContent(
    config: QualityConfig,
    useKotlinDsl: Boolean,
): String {
    val lines = mutableListOf<String>()
    config.verboseLogging?.let { lines += "verboseLogging.set($it)" }
    config.jvmTarget?.let { lines += "jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.$it)" }
    config.gitHooksPath?.let {
        lines += "gitHooks.set(rootProject.layout.projectDirectory.file(\"$it\"))"
    }
    config.gitHooksEnabled?.let { lines += "gitHooksEnabled.set($it)" }
    config.ktlint?.let { ktlint ->
        ktlint.projectConfigPath?.let {
            lines += "ktlint.projectConfig.set(rootProject.layout.projectDirectory.file(\"$it\"))"
        }
        ktlint.sources?.let { sources ->
            lines += sources.slotLines("ktlint.sources", useKotlinDsl)
        }
        ktlint.cli?.let { slot ->
            lines += slot.slotLines("ktlint.cli")
        }
    }
    config.detekt?.let { detekt ->
        detekt.ignoredBuildTypes?.let { lines += "detekt.ignoredBuildTypes.set(${it.toGroovyList(useKotlinDsl)})" }
        detekt.sources?.let { sources ->
            lines += sources.slotLines("detekt.sources", useKotlinDsl)
        }
        detekt.typeResolution?.let { lines += "detekt.typeResolution.set($it)" }
        lines += detekt.kotlin.platformLines("kotlin")
        lines += detekt.android.platformLines("android")
        lines += detekt.compose.platformLines("compose")
    }

    config.extraExtensionContent?.let { extra ->
        lines += extra.trimIndent().lines()
    }

    val extensionBlock =
        if (lines.isEmpty()) {
            ""
        } else {
            """
            appQualityFoundation {
                ${lines.joinToString("\n            ")}
            }
            """.trimIndent()
        }

    val pluginsBlock =
        if (useKotlinDsl) {
            "id(\"ru.kode.android.app-quality.foundation\")"
        } else {
            "id 'ru.kode.android.app-quality.foundation'"
        }
    return """
        plugins {
            $pluginsBlock
        }

        $extensionBlock
        """.trimIndent().removeBlankLines()
}

private fun PlatformDetektBlock?.platformLines(platform: String): List<String> {
    if (this == null) return emptyList()
    val lines = mutableListOf<String>()
    projectConfigPath?.let {
        lines += "detekt.$platform.projectConfig.set(rootProject.layout.projectDirectory.file(\"$it\"))"
    }
    rules?.let { slot ->
        lines += slot.slotLines("detekt.$platform.rules")
    }
    return lines
}

/**
 * Emits the Groovy DSL for one ExternalDependencyConfig slot as a CLOSURE block — exercising
 * both the Closure overload of the slot method and the DependencyCollector call-sugar
 * (`from 'g:n:v'`, `from files(...)`) that consumers use.
 */
private fun DependencySlot.slotLines(path: String): List<String> {
    val inner = mutableListOf<String>()
    refs?.forEach { ref -> inner += "from($ref)" }
    notations?.forEach { notation -> inner += "from(\"$notation\")" }
    files?.let { paths -> inner += "from(files(${paths.toGroovyRootFiles()}))" }
    useDefaults?.let { inner += "useDefaults.set($it)" }
    if (inner.isEmpty()) return emptyList()
    return listOf("$path {") + inner.map { "    $it" } + "}"
}

/**
 * Emits the DSL for one SourcePatternsConfig slot as a block: `include`/`exclude` are
 * plain ListProperty assignments, no `!` prefix (the plugin adds it for ktlint CLI args).
 */
private fun SourcePatternsSlot.slotLines(
    path: String,
    useKotlinDsl: Boolean = false,
): List<String> {
    val inner = mutableListOf<String>()
    include?.let { inner += "include.set(${it.toGroovyList(useKotlinDsl)})" }
    exclude?.let { inner += "exclude.set(${it.toGroovyList(useKotlinDsl)})" }
    useDefaults?.let { inner += "useDefaults.set($it)" }
    if (inner.isEmpty()) return emptyList()
    return listOf("$path {") + inner.map { "    $it" } + "}"
}

private fun List<String>.toGroovyRootFiles(): String {
    return joinToString { path -> "rootProject.layout.projectDirectory.file(\"$path\")" }
}

private fun ModuleSpec.packageName(): String = name.replace('-', '_')

private fun pluginId(
    id: String,
    useKotlinDsl: Boolean,
): String = if (useKotlinDsl) "id(\"$id\")" else "id '$id'"

private fun moduleBuildFileContent(
    module: ModuleSpec,
    useKotlinDsl: Boolean,
): String {
    return when (module.type) {
        ModuleType.AndroidApp, ModuleType.AndroidLib -> {
            val androidPluginId =
                if (module.type == ModuleType.AndroidApp) {
                    "com.android.application"
                } else {
                    "com.android.library"
                }
            val defaultConfigBlock =
                if (module.type == ModuleType.AndroidApp) {
                    if (useKotlinDsl) {
                        """
                    defaultConfig {
                        applicationId = "ru.kode.test.${module.packageName()}"
                        minSdk = 26
                        targetSdk = ${module.compileSdk}
                        versionCode = 1
                        versionName = "1.0"
                    }
                    """
                    } else {
                        """
                    defaultConfig {
                        applicationId "ru.kode.test.${module.packageName()}"
                        minSdk 26
                        targetSdk ${module.compileSdk}
                        versionCode 1
                        versionName "1.0"
                    }
                    """
                    }
                } else if (useKotlinDsl) {
                    """
                defaultConfig {
                    minSdk = 26
                }
                """
                } else {
                    """
                defaultConfig {
                    minSdk 26
                }
                """
                }
            val buildTypesBlock =
                module.buildTypes
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = "\n", prefix = "buildTypes {\n", postfix = "\n}") {
                        if (useKotlinDsl) "    create(\"$it\") { }" else "    $it { }"
                    }
                    .orEmpty()
            val kotlinAndroidPlugin =
                if (module.applyMultiplatformPlugin) {
                    pluginId("org.jetbrains.kotlin.multiplatform", useKotlinDsl)
                } else if (module.applyKotlinAndroidPlugin) {
                    pluginId("org.jetbrains.kotlin.android", useKotlinDsl)
                } else {
                    ""
                }
            val composePlugin =
                if (module.applyJetbrainsComposePlugin) {
                    // Since Compose Multiplatform 1.6.10, org.jetbrains.compose requires the
                    // Kotlin compose compiler plugin applied alongside it.
                    pluginId("org.jetbrains.compose", useKotlinDsl) + "\n" +
                        pluginId("org.jetbrains.kotlin.plugin.compose", useKotlinDsl)
                } else if (module.applyComposePlugin) {
                    pluginId("org.jetbrains.kotlin.plugin.compose", useKotlinDsl)
                } else {
                    ""
                }
            // Keep Java and Kotlin JVM targets aligned, otherwise KGP fails compilation
            // with "Inconsistent JVM Target Compatibility".
            val jvmTargetAlignment =
                if (module.applyKotlinAndroidPlugin) {
                    if (useKotlinDsl) {
                        """
                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }
                    kotlinOptions {
                        jvmTarget = "17"
                    }
                    """
                    } else {
                        """
                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_17
                        targetCompatibility JavaVersion.VERSION_17
                    }
                    kotlinOptions {
                        jvmTarget = "17"
                    }
                    """
                    }
                } else {
                    ""
                }
            val compileSdkLine =
                if (useKotlinDsl) "compileSdk = ${module.compileSdk}" else "compileSdk ${module.compileSdk}"
            val androidTargetBlock =
                if (module.applyMultiplatformPlugin) {
                    """
                kotlin {
                    androidTarget()
                }
                """
                } else {
                    ""
                }
            """
            plugins {
                ${pluginId(androidPluginId, useKotlinDsl)}
                $kotlinAndroidPlugin
                $composePlugin
            }

            android {
                namespace = "ru.kode.test.${module.packageName()}"
                $compileSdkLine

                $jvmTargetAlignment

                $defaultConfigBlock

                $buildTypesBlock
            }

            $androidTargetBlock
            """.trimIndent().removeBlankLines()
        }

        ModuleType.KotlinJvm -> {
            val kotlinPlugin =
                if (module.applyMultiplatformPlugin) {
                    pluginId("org.jetbrains.kotlin.multiplatform", useKotlinDsl)
                } else {
                    pluginId("org.jetbrains.kotlin.jvm", useKotlinDsl)
                }
            val composePlugin =
                if (module.applyJetbrainsComposePlugin) {
                    pluginId("org.jetbrains.compose", useKotlinDsl) + "\n" +
                        pluginId("org.jetbrains.kotlin.plugin.compose", useKotlinDsl)
                } else if (module.applyComposePlugin) {
                    pluginId("org.jetbrains.kotlin.plugin.compose", useKotlinDsl)
                } else {
                    ""
                }
            val kotlinBlock =
                if (module.applyMultiplatformPlugin) {
                    """
                kotlin {
                    jvm()
                }
                """
                } else {
                    ""
                }
            """
            plugins {
                $kotlinPlugin
                $composePlugin
            }

            $kotlinBlock
            """.trimIndent().removeBlankLines()
        }

        ModuleType.JavaOnly ->
            """
            plugins {
                ${pluginId("java", useKotlinDsl)}
            }
            """.trimIndent()
    }
}

private fun libsCatalogContent(catalog: LibsCatalog): String {
    val libraries = mutableListOf<String>()
    if (catalog.includeKtlintCli) {
        libraries += """ktlint-cli = { module = "com.pinterest.ktlint:ktlint-cli", version.ref = "ktlintCli" }"""
    }
    if (catalog.includeDetektFormatting) {
        val module = "io.gitlab.arturbosch.detekt:detekt-formatting"
        libraries +=
            """detekt-formatting = { module = "$module", version.ref = "detekt" }"""
    }
    if (catalog.includeDetektComposeRules) {
        libraries +=
            """detekt-compose-rules = { module = "ru.kode:detekt-rules-compose", version.ref = "detektComposeRules" }"""
    }
    return """
        [versions]
        detekt = "1.23.8"
        ktlintCli = "1.8.0"
        detektComposeRules = "1.4.0"

        [libraries]
        ${libraries.joinToString("\n        ")}
        """.trimIndent()
}

private fun List<String>.toGroovyList(useKotlinDsl: Boolean = false): String =
    if (useKotlinDsl) {
        joinToString(prefix = "listOf(", postfix = ")") { "\"$it\"" }
    } else {
        joinToString(prefix = "[", postfix = "]") { "'$it'" }
    }

private fun String.removeBlankLines(): String {
    return lines()
        .filter { line -> line.isNotBlank() }
        .joinToString("\n")
}

private fun androidSdkPath(): String? {
    val fromEnv = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (fromEnv != null) return fromEnv
    val candidates =
        listOf(
            File(System.getProperty("user.home"), "Library/Android/sdk"),
            File("/Volumes/Projects/library/Android/sdk"),
        )
    return candidates.firstOrNull { it.isDirectory }?.absolutePath
}

private fun writeFileVerbose(
    destination: File,
    content: String,
) {
    println("--- ${destination.path} START ---")
    println(content)
    println("--- ${destination.path} END ---")
    destination.writeText(content)
}
