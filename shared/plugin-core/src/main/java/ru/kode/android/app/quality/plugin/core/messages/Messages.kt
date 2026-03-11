package ru.kode.android.app.quality.plugin.core.messages

fun requiredConfigurationNotFoundMessage(
    name: String,
    defaultName: String,
): String {
    return """
        
        |============================================================
        |                    CONFIGURATION ERROR    
        |============================================================
        | Required configuration not found
        |
        | Expected one of these configurations:
        |   - $name
        |   - $defaultName (fallback)
        |
        | POSSIBLE CAUSES:
        |   1. The configuration was not registered in the build script
        |   2. The configuration name is incorrect
        |   3. There are syntax errors in the build script
        |
        | ACTION REQUIRED:
        |   1. Verify the configuration names in your build script
        |   2. Check for any syntax errors
        |   3. Ensure the plugin is applied correctly
        |   4. Try to run task with --stacktrace option to get more details
        |============================================================
        """.trimMargin()
}
