@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.agp) apply false
    alias(libs.plugins.publish) apply false
    id("ru.kode.android.app-quality.foundation")
}

tasks.register("assemble") {
    subprojects.forEach { subproject ->
        dependsOn(subproject.tasks.matching { it.name == "assemble" })
    }
}

appQualityFoundation {
    verboseLogging.set(true)
    ktlint.projectConfig.set(rootProject.layout.projectDirectory.file(".editorconfig"))
    detekt.kotlin.rules {
        from(files(rootProject.layout.projectDirectory.file("libs/detekt-rules-1.4.0.jar")))
    }
}
