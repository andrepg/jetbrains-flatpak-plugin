package io.github.andrepg.flatpak.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.andrepg.shared.log.Log
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Utility class for reading Flatpak manifest files.
 *
 * Provides access to the application id (`app-id`/`id`), the `sdk` and the
 * `runtime` fields in both JSON and YAML manifests.
 */
object FlatpakManifestReader {
    private val log = Log.getInstance(FlatpakManifestReader::class.java)

    /**
     * Reads the application ID from a Flatpak manifest file.
     *
     * @param manifestPath The path to the Flatpak manifest file
     * @return The application ID if found, or null if the file cannot be read or parsed
     */
    fun readAppId(manifestPath: String): String? {
        val fields = readFields(manifestPath, "app-id", "id")
        return fields["app-id"] ?: fields["id"]
    }

    /**
     * Reads the `sdk` field from a Flatpak manifest file (e.g. `org.gnome.Sdk`).
     *
     * @param manifestPath The path to the Flatpak manifest file
     * @return The SDK app-id if found, or null if the file cannot be read or parsed
     */
    fun readSdk(manifestPath: String): String? = readFields(manifestPath, "sdk")["sdk"]

    /**
     * Reads the `runtime` field from a Flatpak manifest file (e.g. `org.gnome.Platform`).
     *
     * @param manifestPath The path to the Flatpak manifest file
     * @return The runtime app-id if found, or null if the file cannot be read or parsed
     */
    fun readRuntime(manifestPath: String): String? = readFields(manifestPath, "runtime")["runtime"]

    /**
     * Reads the `command` field from a Flatpak manifest file (e.g. `com.example.App`).
     *
     * @param manifestPath The path to the Flatpak manifest file
     * @return The command run inside the sandbox if found, or null if the file cannot be read or parsed
     */
    fun readCommand(manifestPath: String): String? = readFields(manifestPath, "command")["command"]

    /**
     * Reads several fields from a Flatpak manifest file in a single file read/parse.
     *
     * @param manifestPath The path to the Flatpak manifest file
     * @param keys The manifest field names to extract
     * @return A map of key to field value; missing keys map to null
     */
    fun readFields(manifestPath: String, vararg keys: String): Map<String, String?> {
        val file = File(manifestPath)
        if (!file.exists() || file.isDirectory) return emptyMap()
        return try {
            val content = file.readText()
            if (isJson(content, file.extension)) {
                val json = Gson().fromJson(content, JsonObject::class.java)
                keys.associateWith { json.get(it)?.asString }
            } else {
                val yaml = Yaml().load<Map<String, Any>>(content)
                keys.associateWith { yaml[it]?.toString() }
            }
        } catch (e: Exception) {
            log.warn("Could not read Flatpak manifest: $manifestPath", e)
            emptyMap()
        }
    }

    private fun isJson(content: String, extension: String?): Boolean =
        extension?.equals("json", ignoreCase = true) == true || content.trimStart().startsWith("{")
}
