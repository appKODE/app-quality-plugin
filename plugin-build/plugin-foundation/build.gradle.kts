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
    // Engine 2: the consumer provides dev.detekt next to this plugin (checked at apply time).
    compileOnly(libs.detekt2.plugin)

    compileOnly(libs.agp)

    testImplementation(platform(libs.junitBom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val generateDefaultToolVersions =
    tasks.register<WriteProperties>("generateDefaultToolVersions") {
        destinationFile.set(layout.buildDirectory.file("generated/resources/default-tool-versions.properties"))
        listOf(
            "ktlint-cli",
            "detekt-formatting",
            "detekt-compose-rules",
            "detekt-rules",
            "detekt-rules-detekt2",
            "detekt-rules-compose-detekt2",
            "detekt-rules-ktlint-wrapper",
        ).forEach { alias ->
            val library = versionCatalog.findLibrary(alias).get().get()
            property(
                alias,
                "${library.module.group}:${library.module.name}:${library.versionConstraint.requiredVersion}",
            )
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

// Guards the 3.0 publication shape: kode rules come from Maven coordinates (no bundled jar) and
// detekt 2 stays a consumer-provided plugin (not a dependency of this artifact).
val verifyPublication =
    tasks.register("verifyPublication") {
        group = "verification"
        val jarFile = tasks.jar.flatMap { it.archiveFile }
        val jarContent = zipTree(jarFile)
        val publications = fileTree(layout.buildDirectory.dir("publications"))
        val coordinates = "${project.group}:${base.archivesName.get()}:${project.version}"
        dependsOn(tasks.withType<GenerateMavenPom>(), tasks.withType<GenerateModuleMetadata>())
        inputs.file(jarFile)
        inputs.files(publications)
        doLast {
            val bundledJars = jarContent.filter { it.name.endsWith(".jar") }.map { it.name }
            check(bundledJars.isEmpty()) { "Plugin jar bundles jars: $bundledJars" }
            val poms = publications.filter { it.name.endsWith(".xml") }
            publications.forEach { file ->
                val text = file.readText()
                check("dev.detekt" !in text) { "$file declares a dev.detekt dependency" }
            }
            val (group, artifact, version) = coordinates.split(":")
            val markerPom = poms.single { ".gradle.plugin</artifactId>" in it.readText() }.readText()
            check(listOf(group, artifact, version).all { ">$it<" in markerPom }) {
                "The plugin marker POM does not point at $coordinates"
            }
            check(poms.any { "<artifactId>detekt-gradle-plugin</artifactId>" in it.readText() }) {
                "The main POM lost its detekt 1 Gradle plugin dependency"
            }
        }
    }

tasks.named("check") { dependsOn(verifyPublication) }
