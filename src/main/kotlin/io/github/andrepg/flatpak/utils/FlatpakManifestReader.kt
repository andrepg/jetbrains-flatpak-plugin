package io.github.andrepg.flatpak.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.Logger
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Utility class for reading Flatpak manifest files.
 *
 * Provides access to the application id (`app-id`/`id`), the `sdk` and the
 * `runtime` fields in both JSON and YAML manifests.
 */
object FlatpakManifestReader {
    private val logger = Logger.getInstance(FlatpakManifestReader::class.java)

    /**
     * Reads the application ID from a Flatpak manifest file.
     *
     * @param manifestPath The path to the Flatpak manifest file
     * @return The application ID if found, or null if the file cannot be read or parsed
     */
    fun readAppId(manifestPath: String): String? =
        readField(manifestPath, "app-id") ?: readField(manifestPath, "id")

    /**
     * Reads the `sdk` field from a Flatpak manifest file (e.g. `org.gnome.Sdk`).
     *
     * @param manifestPath The path to the Flatpak manifest file
     * @return The SDK app-id if found, or null if the file cannot be read or parsed
     */
    fun readSdk(manifestPath: String): String? = readField(manifestPath, "sdk")

    /**
     * Reads the `runtime` field from a Flatpak manifest file (e.g. `org.gnome.Platform`).
     *
     * @param manifestPath The path to the Flatpak manifest file
     * @return The runtime app-id if found, or null if the file cannot be read or parsed
     */
    fun readRuntime(manifestPath: String): String? = readField(manifestPath, "runtime")

    private fun readField(manifestPath: String, key: String): String? {
        val file = File(manifestPath)
        if (!file.exists() || file.isDirectory) return null
        return try {
            val content = file.readText()
            if (isJson(content, file.extension)) jsonField(content, key) else yamlField(content, key)
        } catch (e: Exception) {
            logger.warn("Could not read Flatpak manifest: $manifestPath", e)
            null
        }
    }

    private fun isJson(content: String, extension: String?): Boolean =
        extension?.equals("json", ignoreCase = true) == true || content.trimStart().startsWith("{")

    private fun jsonField(content: String, key: String): String? {
        val json = Gson().fromJson(content, JsonObject::class.java)
        return json.get(key)?.asString
    }

    private fun yamlField(content: String, key: String): String? {
        val yaml = Yaml().load<Map<String, Any>>(content)
        return yaml[key]?.toString()
    }
}
