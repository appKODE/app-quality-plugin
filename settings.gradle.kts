pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

rootProject.name = ("app-quality")

includeBuild("example-project")
includeBuild("plugin-build")
includeBuild("plugin-test")
includeBuild("build-conventions")