plugins {
    id("kotlin-convention")
    id("plugin-convention")
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish")
}

base {
    archivesName.set("app-quality-foundation")
}

dependencies {
    implementation(gradleApi())
    implementation(libs.detekt.plugin)

    compileOnly(libs.agp)
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val generateDefaultToolVersions =
    tasks.register<WriteProperties>("generateDefaultToolVersions") {
        destinationFile.set(layout.buildDirectory.file("generated/resources/default-tool-versions.properties"))
        listOf("ktlint-cli", "detekt-formatting", "detekt-compose-rules").forEach { alias ->
            val library = versionCatalog.findLibrary(alias).get().get()
            property(alias, "${library.module.group}:${library.module.name}:${library.versionConstraint.requiredVersion}")
        }
    }

sourceSets {
    main {
        resources.srcDir(generateDefaultToolVersions.map { it.destinationFile.get().asFile.parentFile })
    }
}

gradlePlugin {
    website.set("https://github.com/appKODE/app-quality-plugin")
    vcsUrl.set("https://github.com/appKODE/app-quality-plugin")

    plugins {
        create("ru.kode.android.app-quality.foundation") {
            id = "ru.kode.android.app-quality.foundation"
            displayName = "Centralized ktlint and detekt configuration for Android/Kotlin projects"
            implementationClass = "ru.kode.android.app.quality.plugin.foundation.AppQualityFoundationPlugin"
            version = project.version
            description =
                "Gradle plugin that centralizes static analysis and formatting: configures detekt per module, " +
                "runs ktlint through CLI and provides aggregate verification tasks (pipelineCheck, prePushCheck)"
            tags.set(listOf("quality", "detekt", "ktlint", "static-analysis", "verification"))
        }
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            groupId = project.group.toString()
            artifactId = base.archivesName.get()
            version = project.version.toString()
        }
    }
    repositories {
        mavenLocal()
    }
}

tasks.register("setupPluginUploadFromEnvironment") {
    doLast {
        val key = System.getenv("GRADLE_PUBLISH_KEY")
        val secret = System.getenv("GRADLE_PUBLISH_SECRET")

        if (key == null || secret == null) {
            throw GradleException("gradlePublishKey and/or gradlePublishSecret are not defined environment variables")
        }

        System.setProperty("gradle.publish.key", key)
        System.setProperty("gradle.publish.secret", secret)
    }
}
