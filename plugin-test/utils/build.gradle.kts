plugins {
    id("kotlin-convention")
}

group = "ru.kode.android"
version = libs.versions.appQualityShared.get()

dependencies {
    implementation(gradleApi())
    implementation(gradleTestKit())
    implementation(libs.grgitCore)
}
