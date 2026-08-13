package io.github.andrepg.flatpak.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.Logger
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Utility class for reading Flatpak manifest files and extracting application IDs.
 *
 * This class provides functionality to read Flatpak manifest files in JSON or YAML format
 * and extract the application ID from them. It supports both 'app-id' and 'id' fields in the manifest.
 */
object FlatpakManifestReader {
    private val logger = Logger.getInstance(FlatpakManifestReader::class.java)

    /**
     * Reads the application ID from a Flatpak manifest file.
     *
     * @param manifestPath The path to the Flatpak manifest file
     * @return The application ID if found, or null if the file cannot be read or parsed
     */
    fun readAppId(manifestPath: String): String? {
        val file = File(manifestPath)
        if (!file.exists() || file.isDirectory) return null
        return try {
            parseAppId(file.readText(), file.extension)
        } catch (e: Exception) {
            logger.warn("Could not read Flatpak manifest: $manifestPath", e)
            null
        }
    }

    /**
     * Parses the application ID from the content of a Flatpak manifest file.
     *
     * @param content The content of the Flatpak manifest file
     * @param extension The file extension of the manifest file
     * @return The application ID if found, or null if parsing fails
     */
    private fun parseAppId(content: String, extension: String?): String? {
        val isJson = extension?.equals("json", ignoreCase = true) == true
                || content.trimStart().startsWith("{")
        return if (isJson) parseJsonAppId(content) else parseYamlAppId(content)
    }

    /**
     * Parses the application ID from a JSON-formatted Flatpak manifest.
     *
     * @param content The JSON content of the Flatpak manifest file
     * @return The application ID if found, or null if parsing fails
     */
    private fun parseJsonAppId(content: String): String? {
        val json = Gson().fromJson(content, JsonObject::class.java)
        return json.get("app-id")?.asString ?: json.get("id")?.asString
    }

    /**
     * Parses the application ID from a YAML-formatted Flatpak manifest.
     *
     * @param content The YAML content of the Flatpak manifest file
     * @return The application ID if found, or null if parsing fails
     */
    private fun parseYamlAppId(content: String): String? {
        val yaml = Yaml().load<Map<String, Any>>(content)
        return yaml["app-id"]?.toString() ?: yaml["id"]?.toString()
    }
}