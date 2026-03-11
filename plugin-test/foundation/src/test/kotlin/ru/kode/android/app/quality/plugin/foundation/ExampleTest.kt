package ru.kode.android.app.quality.plugin.foundation

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.kode.android.app.quality.plugin.test.utils.BuildType
import ru.kode.android.app.quality.plugin.test.utils.FoundationConfig
import ru.kode.android.app.quality.plugin.test.utils.createAndroidProject
import java.io.File
import java.io.IOException

class ExampleTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        projectDir = File(tempDir, "test-project")
    }

    @Test
    @Throws(IOException::class)
    fun `bundle creates renamed file of debug build from one tag, one commit, build types only`() {
        projectDir.createAndroidProject(
            buildTypes = listOf(BuildType("debug"), BuildType("release")),
            foundationConfig =
                FoundationConfig(
                    output =
                        FoundationConfig.Output(
                            baseFileName = "autotest",
                        ),
                ),
        )

    }
}
