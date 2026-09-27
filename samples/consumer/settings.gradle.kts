// Consumes the plugin as published (./gradlew -p plugin-build publishToMavenLocal), not as an
// included build, to check the artifact a real project gets.
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "aqp-sample-consumer"

include("lib")
