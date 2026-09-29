plugins {
    kotlin("jvm") version "2.4.20" apply false
    // Only used with -Pru.kode.appQuality.detektEngine=2; must be on the build classpath then.
    id("dev.detekt") version "2.0.0-alpha.6" apply false
    id("ru.kode.android.app-quality.foundation") version "3.1.0"
}
