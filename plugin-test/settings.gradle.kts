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
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = ("ru.kode.android.app.quality.plugin-test")

include("foundation")
include("utils")
includeBuild("../plugin-build") {
    dependencySubstitution {
        substitute(module("ru.kode.android:plugin-foundation"))
            .using(project(":plugin-foundation"))
    }
}

includeBuild("../build-conventions")
includeBuild("../shared") {
    dependencySubstitution {
        substitute(module("ru.kode.android:plugin-core"))
            .using(project(":plugin-core"))
    }
}
