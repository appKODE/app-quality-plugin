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

    testImplementation(libs.plugin.core)
    testImplementation(project(":utils"))

    testImplementation(gradleApi())
    testImplementation(libs.grgitCore)

    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junitBom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        val isCI = System.getenv("CI") == "true"
        showStackTraces = true
        showExceptions = true
        showCauses = true
        showStandardStreams = !isCI
    }
}
