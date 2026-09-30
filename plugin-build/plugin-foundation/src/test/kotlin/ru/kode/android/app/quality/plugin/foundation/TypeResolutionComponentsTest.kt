package ru.kode.android.app.quality.plugin.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeResolutionComponentsTest {
    private val defaultBuildTypes = listOf("release", "internal", "external", "demo")
    private val defaultComponents = listOf("AndroidTest")

    @Test
    fun `a custom release build type is dropped by substring`() {
        val variants = mapOf("debug" to "debug", "release" to "release", "releaseGoogle" to "releaseGoogle")

        assertEquals(
            listOf("debug", "debugUnitTest"),
            typeResolutionComponents(variants, defaultBuildTypes, defaultComponents),
        )
    }

    @Test
    fun `a flavor containing a default ignored build type is dropped`() {
        val variants = mapOf("demoDebug" to "debug", "fullDebug" to "debug")

        assertEquals(
            listOf("fullDebug", "fullDebugUnitTest"),
            typeResolutionComponents(variants, defaultBuildTypes, defaultComponents),
        )
    }

    @Test
    fun `both lists ignore case`() {
        val variants = mapOf("debug" to "debug", "Release" to "Release")

        assertEquals(
            listOf("debug"),
            typeResolutionComponents(variants, listOf("RELEASE"), listOf("unittest", "androidtest")),
        )
    }

    @Test
    fun `a flavor is dropped by name`() {
        val variants = mapOf("googleDebug" to "debug", "ruStoreDebug" to "debug")

        assertEquals(
            listOf("googleDebug", "googleDebugUnitTest"),
            typeResolutionComponents(variants, defaultBuildTypes, defaultComponents + "ruStore"),
        )
    }

    @Test
    fun `an empty ignored list keeps androidTest`() {
        assertEquals(
            listOf("debug", "debugUnitTest", "debugAndroidTest"),
            typeResolutionComponents(mapOf("debug" to "debug"), defaultBuildTypes, emptyList()),
        )
    }
}
