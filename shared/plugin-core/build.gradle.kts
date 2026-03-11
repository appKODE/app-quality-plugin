plugins {
    id("kotlin-convention")
    id("maven-publish")
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.vanniktech.maven.publish)
}

group = "ru.kode.android"
version = libs.versions.appQualityShared.get()

dependencies {
    implementation(gradleApi())
    implementation(libs.grgitCore)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.serializationJson)

    compileOnly(libs.agp)
}

mavenPublishing {
    coordinates(artifactId = "app-quality-core")

    publishToMavenCentral()
    signAllPublications()

    pom {

        name.set("App Quality Core")
        description.set("Core library to use inside App Quality plugins ")
        inceptionYear.set("2025")
        url.set("https://github.com/appKODE/app-quality-plugin")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("dmitrii.suzdalev.dz")
                name.set("DIma")
                email.set("dz@kode.ru")
            }
        }

        scm {
            url.set("https://github.com/appKODE/app-quality-plugin")
            connection.set("scm:git:https://github.com/appKODE/app-quality-plugin.git")
            developerConnection.set("scm:git:ssh://git@github.com:appKODE/app-quality-plugin.git")
        }
    }
}
