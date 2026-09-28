package ru.kode.android.app.quality.plugin.foundation

import ru.kode.android.app.quality.plugin.test.utils.CUSTOM_RULES_JAR_PATH
import ru.kode.android.app.quality.plugin.test.utils.DependencySlot
import ru.kode.android.app.quality.plugin.test.utils.DetektBlock
import ru.kode.android.app.quality.plugin.test.utils.PlatformDetektBlock
import ru.kode.android.app.quality.plugin.test.utils.resolveJars
import java.io.File

/**
 * Kotlin sources with known quality-rule behavior, shared across the suite.
 */
object Sources {
    /** Clean under indent_size=2 editorconfig and under the bundled default detekt config. */
    val CLEAN_TWO_SPACE =
        """
        |package ru.kode.test
        |
        |fun main() {
        |  println("ok")
        |}
        |
        """.trimMargin()

    /** Clean under indent_size=4 editorconfig, violates indent under indent_size=2. */
    val CLEAN_FOUR_SPACE =
        """
        |package ru.kode.test
        |
        |fun main() {
        |    println("ok")
        |}
        |
        """.trimMargin()

    /**
     * Contains one 80-char comment line: passes MaxLineLength=120 (bundled default),
     * fails MaxLineLength=60 (custom test config). A comment triggers no other rules.
     */
    val LONG_LINE_80 =
        """
        |package ru.kode.test
        |
        |// ${"x".repeat(77)}
        |
        |fun main() {
        |  println("ok")
        |}
        |
        """.trimMargin()

    /**
     * `value?.length` on a non-null `String` param: syntactically legal Kotlin (a compiler
     * warning, not an error), but only detectable as an UNNECESSARY safe call once the
     * analyzer knows `value` is non-null — which requires type resolution.
     */
    val UNNECESSARY_SAFE_CALL =
        """
        |package ru.kode.test
        |
        |fun printLength(value: String) {
        |  println(value?.length)
        |}
        |
        """.trimMargin()
}

object Configs {
    val EDITORCONFIG_INDENT_2 =
        """
        |root = true
        |
        |[*.{kt,kts}]
        |indent_style = space
        |indent_size = 2
        |max_line_length = 120
        |insert_final_newline = true
        |
        """.trimMargin()

    val EDITORCONFIG_INDENT_4 =
        """
        |root = true
        |
        |[*.{kt,kts}]
        |indent_style = space
        |indent_size = 4
        |max_line_length = 120
        |insert_final_newline = true
        |
        """.trimMargin()

    /** Complete-enough detekt config: any line longer than 60 chars fails. */
    val DETEKT_MAX_LINE_60 =
        """
        |build:
        |  maxIssues: 0
        |
        |style:
        |  MaxLineLength:
        |    active: true
        |    maxLineLength: 60
        |
        """.trimMargin()

    /**
     * Activates rules from BOTH the custom KODE jar and the default detekt-formatting
     * dependency: config validation only accepts each section when the matching artifact is
     * on detektPlugins, and the 2-space Indentation rule fires on 4-space sources.
     */
    val DETEKT_KODE_AND_FORMATTING =
        """
        |build:
        |  maxIssues: 0
        |
        |formatting:
        |  Indentation:
        |    active: true
        |    indentSize: 2
        |
        |kode:
        |  ImmutableDataClass:
        |    active: true
        |
        """.trimMargin()

    /**
     * Activates a rule from the custom KODE rules jar: detekt only accepts this config when
     * the jar is on the detektPlugins classpath (unknown rule set fails config validation).
     */
    val DETEKT_KODE_RULE =
        """
        |build:
        |  maxIssues: 0
        |
        |kode:
        |  ImmutableDataClass:
        |    active: true
        |
        """.trimMargin()

    /**
     * `UnnecessarySafeCall` (potential-bugs) only fires with type resolution enabled: without
     * it, detekt cannot know whether the receiver is actually non-null.
     */
    val DETEKT_UNNECESSARY_SAFE_CALL =
        """
        |build:
        |  maxIssues: 0
        |
        |potential-bugs:
        |  UnnecessarySafeCall:
        |    active: true
        |
        """.trimMargin()
}

/**
 * Wires the jar that `rulesJar` copies to [CUSTOM_RULES_JAR_PATH] into `detekt.kotlin.rules` —
 * the file-based custom-rules setup, for projects without an android module (where the
 * `ru.kode:detekt-rules` default would already provide the `kode` rule set).
 */
fun kodeRulesJarBlock(): DetektBlock =
    DetektBlock(
        kotlin =
            PlatformDetektBlock(
                // Stacks ON TOP of the default detekt-formatting.
                rules = DependencySlot(files = listOf(CUSTOM_RULES_JAR_PATH)),
            ),
    )

/** The `kode` rule-set jar for [engine], resolved from Maven Central, for file-wiring tests. */
fun exampleRulesJar(engine: Int = 1): File {
    val artifact = if (engine == 1) "detekt-rules" else "detekt-rules-detekt2"
    return resolveJars("ru.kode:$artifact:$KODE_RULES_VERSION").single { it.name.startsWith(artifact) }
}

const val KODE_RULES_VERSION = "2.0.0"

/**
 * Pre-built-in-Kotlin toolchain used to test variant detekt tasks (detektDebug/detektRelease):
 * detekt 1.x registers them only for the classic org.jetbrains.kotlin.android setup.
 * KGP 2.2.10 on the test classpath supports AGP up to 8.x; AGP 8.7.3 requires Gradle 8.9+.
 */
const val LEGACY_AGP_VERSION = "8.7.3"
const val LEGACY_GRADLE_VERSION = "8.14"
