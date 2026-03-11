package ru.kode.android.app.quality.plugin.test.utils

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class AlwaysInfoLogger : Logger by Logging.getLogger("AppQualityTest") {
    override fun info(message: String?) {
        println("[INFO] $message")
    }

    override fun warn(message: String?) {
        println("[WARN] $message")
    }

    override fun error(message: String?, exception: Throwable?) {
        println("[ERROR] $message, ${exception?.message}")
    }

    override fun quiet(message: String?) {
        println("[QUIET] $message")
    }
}
