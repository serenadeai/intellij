package ai.serenade.intellij.services

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.nio.file.Files
import java.nio.file.Paths

@Serializable
data class SettingsFile(
    val installed: Boolean? = null
)

class Settings {
    private val fileName = Paths.get(
        System.getProperty("user.home"),
        ".serenade",
        "serenade.json"
    )

    private var settingsFile: String = try {
        Files.readAllLines(fileName).joinToString(separator = "\n")
    } catch (e: Exception) {
        "{}"
    }

    private val json = Json(
        JsonConfiguration.Stable.copy(
            encodeDefaults = false, // don't include all the null values
            ignoreUnknownKeys = true, // don't break on parsing unknown responses
            isLenient = true // empty strings
        )
    )

    private val settings = json.parse(SettingsFile.serializer(), settingsFile)

    fun installed(): Boolean {
        return settings.installed ?: false
    }
}
