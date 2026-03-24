plugins {
    id("kotlin-convention")
    id("plugin-convention")
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish")
    id("com.google.devtools.ksp")
}

base {
    archivesName.set("app-quality-foundation")
}

dependencies {
    implementation(gradleApi())
    implementation(libs.grgitCore)
    implementation(libs.grgitGradle)
    implementation(libs.detekt.plugin)

    compileOnly(libs.agp)
}

gradlePlugin {
    website.set("https://github.com/appKODE/app-quality-plugin")
    vcsUrl.set("https://github.com/appKODE/app-quality-plugin")

    plugins {
        create("ru.kode.android.app-quality.foundation") {
            id = "ru.kode.android.app-quality.foundation"
            displayName = "Configure project output using tag and generate changelog"
            implementationClass = "ru.kode.android.app.quality.plugin.foundation.AppQualityFoundationPlugin"
            version = project.version
            description = "Android plugin to configure output and changelog generation"
            tags.set(listOf("output", "publish", "changelog", "build"))
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
