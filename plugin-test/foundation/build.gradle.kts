plugins {
    id("kotlin-convention")
    id("java-gradle-plugin")
}

dependencies {
    implementation(libs.agp)
    implementation(libs.plugin.foundation)
    // Lets generated test projects apply org.jetbrains.kotlin.plugin.compose from the
    // injected classpath; the version must match the kotlin-gradle-plugin resolved there.
    implementation(libs.compose.compiler.plugin)
    // Lets generated test projects apply org.jetbrains.compose (JetBrains Compose
    // Multiplatform) from the injected classpath.
    implementation(libs.compose.multiplatform.plugin)
    // Lets generated test projects declare id("dev.detekt") for detekt engine 2.
    implementation(libs.detekt2.plugin)

    testImplementation(libs.plugin.core)
    testImplementation(project(":utils"))

    testImplementation(gradleApi())
    testImplementation(libs.grgitCore)

    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junitBom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val isCI = System.getenv("CI") == "true"

tasks.test {
    // The PR tier. The full version matrix is tagged "matrix" and runs via matrixTest.
    useJUnitPlatform { excludeTags("matrix") }
    testLogging {
        showStackTraces = true
        showExceptions = true
        showCauses = true
        showStandardStreams = !isCI
    }
}

tasks.register<Test>("matrixTest") {
    description = "Runs the full engine x Gradle x AGP x Kotlin compatibility matrix."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("matrix") }
    testLogging {
        showStackTraces = true
        showExceptions = true
        showCauses = true
        showStandardStreams = !isCI
    }
}
