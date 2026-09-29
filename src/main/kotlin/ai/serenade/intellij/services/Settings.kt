package ai.serenade.intellij.services

import kotlinx.serialization.Serializable
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

    private val settingsFile: String = try {
        Files.readAllLines(fileName).joinToString(separator = "\n")
    } catch (_: Exception) {
        "{}"
    }

    private val settings = json.decodeFromString<SettingsFile>(settingsFile)

    fun installed(): Boolean = settings.installed ?: false
}
