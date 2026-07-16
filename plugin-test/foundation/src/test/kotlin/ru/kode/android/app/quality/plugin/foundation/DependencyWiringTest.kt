package ru.kode.android.app.quality.plugin.foundation

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.kode.android.app.quality.plugin.foundation.messages.missingDependencyFileMessage
import ru.kode.android.app.quality.plugin.foundation.messages.noEditorConfigFileMessage
import ru.kode.android.app.quality.plugin.test.utils.DependencySlot
import ru.kode.android.app.quality.plugin.test.utils.DetektBlock
import ru.kode.android.app.quality.plugin.test.utils.KtlintBlock
import ru.kode.android.app.quality.plugin.test.utils.LibsCatalog
import ru.kode.android.app.quality.plugin.test.utils.ModuleSpec
import ru.kode.android.app.quality.plugin.test.utils.ModuleType
import ru.kode.android.app.quality.plugin.test.utils.PlatformDetektBlock
import ru.kode.android.app.quality.plugin.test.utils.QualityConfig
import ru.kode.android.app.quality.plugin.test.utils.createQualityProject
import ru.kode.android.app.quality.plugin.test.utils.resolveJars
import ru.kode.android.app.quality.plugin.test.utils.runTask
import ru.kode.android.app.quality.plugin.test.utils.runTaskWithFail
import java.io.File

/**
 * Covers the unified dependency slots (ExternalDependencyConfig): every source kind through
 * one `from(...)` API, defaults + useDefaults semantics, and the actionable error messages.
 */
class DependencyWiringTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        projectDir = File(tempDir, "test-project")
    }

    private fun kotlinModule(name: String = "a") =
        ModuleSpec(
            name = name,
            type = ModuleType.KotlinJvm,
            kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
        )

    @Test
    fun `typed accessors from a custom-named catalog work without any libs catalog`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule()),
            qualityConfig =
                QualityConfig(
                    ktlint =
                        KtlintBlock(
                            cli = DependencySlot(refs = listOf("deps.ktlint.cli"), useDefaults = false),
                        ),
                    detekt =
                        DetektBlock(
                            kotlin =
                                PlatformDetektBlock(
                                    rules =
                                        DependencySlot(
                                            refs = listOf("deps.detekt.formatting"),
                                            useDefaults = false,
                                        ),
                                ),
                        ),
                ),
            libsCatalog = LibsCatalog(name = "deps"),
        )

        val result = projectDir.runTask(":a:detekt")
        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)

        val ktlintResult = projectDir.runTask("ktlintCheck")
        assertEquals(TaskOutcome.SUCCESS, ktlintResult.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `rules by string coordinates work without catalog alias`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule()),
            qualityConfig =
                QualityConfig(
                    detekt =
                        DetektBlock(
                            kotlin =
                                PlatformDetektBlock(
                                    rules =
                                        DependencySlot(
                                            notations = listOf("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8"),
                                            useDefaults = false,
                                        ),
                                ),
                        ),
                ),
            libsCatalog = LibsCatalog(includeDetektFormatting = false),
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `published compose rules consumed by coordinates without catalog alias`() {
        // Simulates the publish-and-consume flow for custom rules: the compose module's
        // bundled config validates its `compose:` rule set only when the published
        // ru.kode:detekt-rules-compose artifact actually lands on detektPlugins.
        projectDir.createQualityProject(
            modules = listOf(kotlinModule().copy(applyComposePlugin = true)),
            qualityConfig =
                QualityConfig(
                    detekt =
                        DetektBlock(
                            compose =
                                PlatformDetektBlock(
                                    rules =
                                        DependencySlot(
                                            notations = listOf("ru.kode:detekt-rules-compose:1.4.0"),
                                            useDefaults = false,
                                        ),
                                ),
                        ),
                ),
            libsCatalog = LibsCatalog(includeDetektComposeRules = false),
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `ktlint cli by string coordinates works without catalog alias`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule()),
            qualityConfig =
                QualityConfig(
                    ktlint =
                        KtlintBlock(
                            cli =
                                DependencySlot(
                                    notations = listOf("com.pinterest.ktlint:ktlint-cli:1.8.0"),
                                    useDefaults = false,
                                ),
                        ),
                ),
            libsCatalog = LibsCatalog(includeKtlintCli = false),
        )

        val result = projectDir.runTask("ktlintCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `ktlint cli from jar files works without any catalog alias`() {
        // Plain resolution yields the thin CLI jar + its transitives — provide the whole set,
        // which is exactly the checked-in-jars use case.
        val cliJars = resolveJars("com.pinterest.ktlint:ktlint-cli:1.8.0")
        assumeTrue(cliJars.isNotEmpty(), "could not resolve the ktlint-cli jars; skipping")

        val toolPaths =
            cliJars.map { jar ->
                jar.copyTo(File(projectDir, "tools/${jar.name}").also { it.parentFile.mkdirs() }, overwrite = true)
                "tools/${jar.name}"
            }
        projectDir.createQualityProject(
            modules = listOf(kotlinModule()),
            qualityConfig =
                QualityConfig(
                    ktlint = KtlintBlock(cli = DependencySlot(files = toolPaths, useDefaults = false)),
                ),
            libsCatalog = LibsCatalog(includeKtlintCli = false),
        )

        val result = projectDir.runTask("ktlintCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `custom rules jar stacks on top of the default formatting rules`() {
        val rulesJar = exampleRulesJar()
        assumeTrue(rulesJar != null, "example-project rules jar not found; skipping")

        // Defaults stay ON: the kode rule set must validate (custom jar present) AND the
        // formatting rule set must validate + fire (default detekt-formatting present) —
        // both artifacts active on detektPlugins at once.
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_KODE_AND_FORMATTING,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_FOUR_SPACE),
                    ),
                ),
            qualityConfig = QualityConfig(detekt = kodeRulesJarBlock()),
            rulesJar = rulesJar,
        )

        val result = projectDir.runTaskWithFail(":a:detekt")

        assertEquals(TaskOutcome.FAILED, result.task(":a:detekt")?.outcome)
        assertTrue(
            result.output.contains("Indentation"),
            "expected the default detekt-formatting Indentation rule to fire alongside the custom jar",
        )
    }

    @Test
    fun `useDefaults false drops the default formatting rules`() {
        val rulesJar = exampleRulesJar()
        assumeTrue(rulesJar != null, "example-project rules jar not found; skipping")

        // Same setup as above but defaults OFF: detekt-formatting is gone, so the config's
        // `formatting:` section now fails validation as an unknown rule set — while the kode
        // section still validates via the explicitly configured jar.
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_KODE_AND_FORMATTING,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_FOUR_SPACE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    detekt =
                        DetektBlock(
                            kotlin =
                                PlatformDetektBlock(
                                    rules =
                                        DependencySlot(
                                            files = listOf("libs/detekt-rules-1.4.0.jar"),
                                            useDefaults = false,
                                        ),
                                ),
                        ),
                ),
            rulesJar = rulesJar,
        )

        val result = projectDir.runTaskWithFail(":a:detekt")

        assertTrue(
            result.output.contains("formatting"),
            "expected a validation error about the now-unknown formatting rule set",
        )
        assertTrue(
            !result.output.contains("Indentation - ["),
            "the formatting rules must not have run with defaults disabled",
        )
    }

    @Test
    fun `nested groovy closure blocks configure the extension end to end`() {
        val rulesJar = exampleRulesJar()
        assumeTrue(rulesJar != null, "example-project rules jar not found; skipping")

        // Exercises the Closure overloads of every level: ktlint { cli { ... } } and
        // detekt { kotlin { rules { ... } } } in a plain Groovy script — the kode rule set
        // only validates if the jar wired through the nested blocks reaches detektPlugins.
        projectDir.createQualityProject(
            modules =
                listOf(
                    ModuleSpec(
                        name = "a",
                        type = ModuleType.KotlinJvm,
                        detektKotlinConfigContent = Configs.DETEKT_KODE_RULE,
                        kotlinSources = mapOf("src/main/kotlin/ru/kode/test/Main.kt" to Sources.CLEAN_TWO_SPACE),
                    ),
                ),
            qualityConfig =
                QualityConfig(
                    extraExtensionContent =
                        """
                        ktlint {
                            cli {
                                useDefaults.set(true)
                            }
                        }
                        detekt {
                            kotlin {
                                rules {
                                    from(files(rootProject.layout.projectDirectory.file("libs/detekt-rules-1.4.0.jar")))
                                }
                            }
                        }
                        """,
                ),
            rulesJar = rulesJar,
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `missing configured rules file fails with actionable message naming the slot`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule()),
            qualityConfig =
                QualityConfig(
                    detekt =
                        DetektBlock(
                            kotlin = PlatformDetektBlock(rules = DependencySlot(files = listOf("libs/nope.jar"))),
                        ),
                ),
        )

        val result = projectDir.runTaskWithFail(":a:detekt")

        assertTrue(result.output.contains("MISSING DEPENDENCY FILE"), "expected the missing file banner")
        assertTrue(
            result.output.contains("detekt.kotlin.rules"),
            "expected the fully qualified slot in the message",
        )
        assertTrue(result.output.contains("from(files("), "expected the from(files(...)) fix snippet")
        val expectedMessage =
            missingDependencyFileMessage(File(projectDir, "libs/nope.jar").canonicalFile, "detekt.kotlin.rules")
        assertTrue(
            result.output.contains(expectedMessage),
            "expected the verbatim missingDependencyFileMessage banner in the output",
        )
    }

    @Test
    fun `missing configured cli file fails with actionable message naming the slot`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule()),
            qualityConfig =
                QualityConfig(
                    ktlint = KtlintBlock(cli = DependencySlot(files = listOf("tools/nope.jar"))),
                ),
        )

        val result = projectDir.runTaskWithFail("ktlintCheck")

        assertTrue(result.output.contains("MISSING DEPENDENCY FILE"), "expected the missing file banner")
        assertTrue(result.output.contains("ktlint.cli"), "expected the fully qualified slot in the message")
        val expectedMessage =
            missingDependencyFileMessage(File(projectDir, "tools/nope.jar").canonicalFile, "ktlint.cli")
        assertTrue(
            result.output.contains(expectedMessage),
            "expected the verbatim missingDependencyFileMessage banner in the output",
        )
    }

    @Test
    fun `no version catalog at all still succeeds via the baked-in defaults`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule()),
            libsCatalog = LibsCatalog(generate = false),
        )

        // No libs.versions.toml at all — every default slot falls back to its baked-in
        // coordinate instead of failing.
        val result = projectDir.runTask("ktlintCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `missing ktlint-cli alias still succeeds via the baked-in default`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule()),
            libsCatalog = LibsCatalog(includeKtlintCli = false),
        )

        val result = projectDir.runTask("ktlintCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `missing detekt-formatting alias still succeeds via the baked-in default`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule()),
            libsCatalog = LibsCatalog(includeDetektFormatting = false),
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `missing detekt-compose-rules alias still succeeds a compose module via the baked-in default`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule().copy(applyComposePlugin = true)),
            libsCatalog = LibsCatalog(includeDetektComposeRules = false),
        )

        val result = projectDir.runTask(":a:detekt")

        assertEquals(TaskOutcome.SUCCESS, result.task(":a:detekt")?.outcome)
    }

    @Test
    fun `from with a pinned version coordinate still resolves cleanly`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule()),
            qualityConfig =
                QualityConfig(
                    extraExtensionContent =
                        """
                        ktlint {
                            cli {
                                from("com.pinterest.ktlint:ktlint-cli:1.8.0")
                                useDefaults.set(false)
                            }
                        }
                        """,
                ),
        )

        val result = projectDir.runTask("ktlintCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintCheck")?.outcome)
    }

    @Test
    fun `ktlint-cli catalog alias wins over the baked-in default when present`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule()),
            libsCatalog = LibsCatalog(generate = false),
            extraRootFiles =
                mapOf(
                    "gradle/libs.versions.toml" to
                        """
                        [versions]
                        ktlintCli = "1.8.0"

                        [libraries]
                        ktlint-cli = { module = "com.pinterest.ktlint:ktlint-cli-BOGUS", version.ref = "ktlintCli" }
                        """.trimIndent(),
                ),
        )

        // A bogus coordinate under the real alias name only breaks the build if the catalog
        // alias was actually used instead of the plugin's baked-in default.
        val result = projectDir.runTaskWithFail("ktlintCheck")

        assertTrue(
            result.output.contains("ktlint-cli-BOGUS"),
            "expected resolution to attempt the catalog's coordinate, got: ${result.output}",
        )
    }

    @Test
    fun `detekt-formatting catalog alias wins over the baked-in default when present`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule()),
            libsCatalog = LibsCatalog(generate = false),
            extraRootFiles =
                mapOf(
                    "gradle/libs.versions.toml" to
                        """
                        [versions]
                        detekt = "1.23.8"

                        [libraries]
                        detekt-formatting = { module = "io.gitlab.arturbosch.detekt:detekt-formatting-BOGUS", version.ref = "detekt" }
                        """.trimIndent(),
                ),
        )

        val result = projectDir.runTaskWithFail(":a:detekt")

        assertTrue(
            result.output.contains("detekt-formatting-BOGUS"),
            "expected resolution to attempt the catalog's coordinate, got: ${result.output}",
        )
    }

    @Test
    fun `detekt-compose-rules catalog alias wins over the baked-in default when present`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule().copy(applyComposePlugin = true)),
            libsCatalog = LibsCatalog(generate = false),
            extraRootFiles =
                mapOf(
                    "gradle/libs.versions.toml" to
                        """
                        [versions]
                        detektComposeRules = "1.4.0"

                        [libraries]
                        detekt-compose-rules = { module = "ru.kode:detekt-rules-compose-BOGUS", version.ref = "detektComposeRules" }
                        """.trimIndent(),
                ),
        )

        val result = projectDir.runTaskWithFail(":a:detekt")

        assertTrue(
            result.output.contains("detekt-rules-compose-BOGUS"),
            "expected resolution to attempt the catalog's coordinate, got: ${result.output}",
        )
    }

    @Test
    fun `explicitly configured missing editorconfig fails with actionable message`() {
        projectDir.createQualityProject(
            modules = listOf(kotlinModule()),
            qualityConfig =
                QualityConfig(
                    ktlint = KtlintBlock(projectConfigPath = "config/nope.editorconfig"),
                ),
        )

        val result = projectDir.runTaskWithFail("ktlintCheck")

        assertTrue(
            result.output.contains("MISSING CONFIGURATION FILE"),
            "expected the missing editorconfig banner",
        )
        assertTrue(
            result.output.contains("ktlint.projectConfig.set"),
            "expected the projectConfig fix option",
        )
        val expectedMessage = noEditorConfigFileMessage(File(projectDir, "config/nope.editorconfig").canonicalFile)
        assertTrue(
            result.output.contains(expectedMessage),
            "expected the verbatim noEditorConfigFileMessage banner in the output",
        )
    }
}
