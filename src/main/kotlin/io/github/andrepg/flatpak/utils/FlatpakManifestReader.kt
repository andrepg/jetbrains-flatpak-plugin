package io.github.andrepg.flatpak.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.andrepg.flatpak.exception.FlatpakManifestException
import io.github.andrepg.shared.log.Log
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Pure-JDK reader for Flatpak manifest files.
 *
 * The core is [parseFields]: it takes manifest *content* as a string and returns
 * the requested fields. It has no file-system or platform dependency, so it runs
 * both inside the IDE and from the `generateBundledGtkSchema` Gradle task.
 *
 * The `readXxx(path)` overloads are the JDK file-system convenience used by
 * standalone tooling and hermetic unit tests; they are deliberately forgiving
 * (missing/unreadable/unparseable manifests degrade to null/empty) because
 * detection and tooling must never crash on a bad file. IDE glue that wants
 * virtual-file reads should use [FlatpakManifestVfsReader] instead.
 */
object FlatpakManifestReader {
    private val log = Log.getInstance(FlatpakManifestReader::class.java)

    /**
     * Parses [content] as a Flatpak manifest and extracts the requested fields.
     *
     * Format is inferred from [fileName]'s extension and content (`{json, yaml,
     * yml}`; anything else is parsed as YAML, since `manifest.yml` covers it).
     *
     * @throws FlatpakManifestException when the content cannot be parsed.
     */
    fun parseFields(
        content: String,
        fileName: String,
        vararg keys: String,
    ): Map<String, String?> {
        val json = isJson(content, fileName.substringAfterLast('.', ""))
        return try {
            if (json) {
                val root = Gson().fromJson(content, JsonObject::class.java)
                keys.associateWith { root.get(it)?.asString }
            } else {
                @Suppress("UNCHECKED_CAST")
                val root = Yaml().load<Map<String, Any>>(content)
                keys.associateWith { root[it]?.toString() }
            }
        } catch (e: Exception) {
            throw FlatpakManifestException("Could not parse Flatpak manifest: $fileName", e)
        }
    }

    /**
     * Reads several fields from a Flatpak manifest file in a single file read/parse.
     *
     * @param manifestPath The path to the Flatpak manifest file
     * @param keys The manifest field names to extract
     * @return A map of key to field value; missing or unparseable keys map to null
     */
    fun readFields(
        manifestPath: String,
        vararg keys: String,
    ): Map<String, String?> {
        val file = File(manifestPath)
        if (!file.exists() || file.isDirectory) return emptyMap()
        return try {
            parseFields(file.readText(), file.name, *keys)
        } catch (e: FlatpakManifestException) {
            log.warn(e.message ?: "Could not read Flatpak manifest: $manifestPath", e)
            emptyMap()
        }
    }

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

    private fun isJson(
        content: String,
        extension: String,
    ): Boolean = extension.equals("json", ignoreCase = true) || content.trimStart().startsWith("{")
}
