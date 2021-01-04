package ai.serenade.intellij.services

import kotlinx.serialization.* // ktlint-disable no-wildcard-imports
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

    private val settings = json.decodeFromString<SettingsFile>(settingsFile)

    fun installed(): Boolean {
        return settings.installed ?: false
    }
}
