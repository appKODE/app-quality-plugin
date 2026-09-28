package ru.kode.android.app.quality.plugin.foundation

import java.io.File

/** Test-resource sources (copied from the detekt-rules smoke sample) under `src/main/kotlin/sample/`. */
fun fixtureSources(dir: String): Map<String, String> {
    val root = File(requireNotNull(EngineFixtureAnchor::class.java.classLoader.getResource("fixtures/$dir")).toURI())
    return root.walkTopDown().filter { it.isFile }.associate {
        "src/main/kotlin/sample/" + it.relativeTo(root).invariantSeparatorsPath to it.readText()
    }
}

private object EngineFixtureAnchor

/**
 * Rule ids reported in [module]'s detekt XML (engine 1) / checkstyle (engine 2) reports: the
 * `source="detekt.<RuleId>"` attributes of every `*.xml` under `build/reports/detekt/`, or only
 * of [reportName] when given.
 */
fun File.reportedRuleIds(
    module: String,
    reportName: String? = null,
): Set<String> =
    File(this, "$module/build/reports/detekt")
        .listFiles { file ->
            file.extension == "xml" && (reportName == null || file.nameWithoutExtension == reportName)
        }
        .orEmpty()
        .flatMap { report ->
            Regex("""source="detekt\.([^"]+)"""").findAll(report.readText()).map { it.groupValues[1] }
        }
        .toSet()

/** The `kode` rule-set artifact for [engine] (`detekt-rules` or `detekt-rules-detekt2`). */
fun kodeRulesArtifact(engine: Int): String = if (engine == 1) "detekt-rules" else "detekt-rules-detekt2"

/** The `kode` compose rule-set artifact for [engine]. */
fun kodeComposeRulesArtifact(engine: Int): String =
    if (engine == 1) "detekt-rules-compose" else "detekt-rules-compose-detekt2"

/** A 2-space clean source with an empty function: only `EmptyFunctionBlock` fires on it. */
const val EMPTY_FUNCTION_SOURCE = "package sample\n\nfun empty() {}\n"

/** A 2-space clean source declaring `foo()`, flagged by the custom `NoFoo` rule. */
const val FOO_SOURCE = "package sample\n\nfun foo(): Int = 1\n"

/** Module config activating only the custom `NoFoo` rule (valid for both engines). */
val NO_FOO_CONFIG =
    """
    |custom:
    |  NoFoo:
    |    active: true
    """.trimMargin()

/** Build-file dependencies of a custom rule-set module for [engine] (Groovy DSL). */
fun customRulesDependencies(engine: Int): String =
    if (engine == 1) {
        "dependencies { compileOnly 'io.gitlab.arturbosch.detekt:detekt-api:1.23.8' }"
    } else {
        """
        dependencies {
            compileOnly 'dev.detekt:detekt-api:2.0.0-alpha.6'
            compileOnly 'dev.detekt:detekt-psi-utils:2.0.0-alpha.6'
        }
        """.trimIndent()
    }

/** Sources (plus the ServiceLoader registration) of a one-rule `custom` rule set for [engine]. */
fun customRulesSources(engine: Int): Map<String, String> =
    if (engine == 1) {
        mapOf(
            "src/main/kotlin/custom/NoFoo.kt" to
                """
                package custom

                import io.gitlab.arturbosch.detekt.api.CodeSmell
                import io.gitlab.arturbosch.detekt.api.Config
                import io.gitlab.arturbosch.detekt.api.Debt
                import io.gitlab.arturbosch.detekt.api.Entity
                import io.gitlab.arturbosch.detekt.api.Issue
                import io.gitlab.arturbosch.detekt.api.Rule
                import io.gitlab.arturbosch.detekt.api.RuleSet
                import io.gitlab.arturbosch.detekt.api.RuleSetProvider
                import io.gitlab.arturbosch.detekt.api.Severity
                import org.jetbrains.kotlin.psi.KtNamedFunction

                class NoFooProvider : RuleSetProvider {
                  override val ruleSetId: String = "custom"

                  override fun instance(config: Config): RuleSet = RuleSet(ruleSetId, listOf(NoFoo(config)))
                }

                class NoFoo(config: Config) : Rule(config) {
                  override val issue: Issue = Issue("NoFoo", Severity.Style, "No foo", Debt.FIVE_MINS)

                  override fun visitNamedFunction(function: KtNamedFunction) {
                    super.visitNamedFunction(function)
                    if (function.name == "foo") report(CodeSmell(issue, Entity.from(function), "foo"))
                  }
                }

                """.trimIndent(),
            "src/main/resources/META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider" to
                "custom.NoFooProvider\n",
        )
    } else {
        mapOf(
            "src/main/kotlin/custom/NoFoo.kt" to
                """
                package custom

                import dev.detekt.api.Config
                import dev.detekt.api.Entity
                import dev.detekt.api.Finding
                import dev.detekt.api.Rule
                import dev.detekt.api.RuleSet
                import dev.detekt.api.RuleSetId
                import dev.detekt.api.RuleSetProvider
                import org.jetbrains.kotlin.psi.KtNamedFunction

                class NoFooProvider : RuleSetProvider {
                  override val ruleSetId: RuleSetId = RuleSetId("custom")

                  override fun instance(): RuleSet = RuleSet(ruleSetId, listOf({ config -> NoFoo(config) }))
                }

                class NoFoo(config: Config) : Rule(config, "No foo") {
                  override fun visitNamedFunction(function: KtNamedFunction) {
                    super.visitNamedFunction(function)
                    if (function.name == "foo") report(Finding(Entity.from(function), "foo"))
                  }
                }

                """.trimIndent(),
            "src/main/resources/META-INF/services/dev.detekt.api.RuleSetProvider" to "custom.NoFooProvider\n",
        )
    }

private val ENGINE_1_ANDROID_DEFAULT_RULE_IDS =
    setOf(
        "EmptyFunctionBlock",
        "ImmutableDataClass",
        "MapperFileNaming",
        "MatchingDeclarationName",
        "PayloadArgumentName",
        "RouteWiringMethodNaming",
        "UnusedParameter",
        "UnusedPrivateProperty",
        "UseDataClass",
        "UseSurfaceModifier",
    )

/**
 * Rule ids the bundled default configs report on the `kode-violations` fixture in an android
 * module, per engine: detekt 2 needs type resolution for UnusedPrivateProperty and UseDataClass.
 */
val ANDROID_DEFAULT_RULE_IDS =
    mapOf(
        1 to ENGINE_1_ANDROID_DEFAULT_RULE_IDS,
        2 to ENGINE_1_ANDROID_DEFAULT_RULE_IDS - setOf("UnusedPrivateProperty", "UseDataClass"),
    )
