package io.github.andrepg.flatpak.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.github.andrepg.flatpak.exception.FlatpakManifestException
import io.github.andrepg.shared.log.Log
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Manifest reading, in one file with two objects (IO policy §3.2):
 *
 * - [FlatpakManifestReader] is the pure-JDK core: [FlatpakManifestReader.parseFields] takes
 *   manifest *content* as a string and returns the requested fields. It has no file-system or
 *   platform dependency, so it runs both inside the IDE and from the `generateBundledGtkSchema`
 *   Gradle task. The `readXxx(path)` overloads are JDK file-system conveniences used by standalone
 *   tooling and hermetic unit tests.
 * - [FlatpakManifestVfsReader] adapts the parser to the IntelliJ VFS ([VirtualFile], or a path via
 *   [LocalFileSystem]) for IDE glue.
 *
 * Everything except [FlatpakManifestReader.parseFields] is deliberately forgiving — missing,
 * unreadable or unparseable manifests degrade to null/empty, because detection and tooling must
 * never crash on a bad file.
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
     * Forgiving variant of [parseFields]: unparseable content logs a warning and degrades to an
     * empty map instead of throwing.
     */
    internal fun parseFieldsForgiving(
        content: String,
        fileName: String,
        vararg keys: String,
    ): Map<String, String?> =
        try {
            parseFields(content, fileName, *keys)
        } catch (e: FlatpakManifestException) {
            log.warn(e.message ?: "Could not parse Flatpak manifest: $fileName", e)
            emptyMap()
        }

    /** Extracts the application ID (`app-id`, falling back to `id`) from parsed fields. */
    internal fun pickAppId(fields: Map<String, String?>): String? = fields["app-id"] ?: fields["id"]

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
        return parseFieldsForgiving(file.readText(), file.name, *keys)
    }

    /**
     * Reads the application ID from a Flatpak manifest file.
     *
     * @param manifestPath The path to the Flatpak manifest file
     * @return The application ID if found, or null if the file cannot be read or parsed
     */
    fun readAppId(manifestPath: String): String? = pickAppId(readFields(manifestPath, "app-id", "id"))

    private fun isJson(
        content: String,
        extension: String,
    ): Boolean = extension.equals("json", ignoreCase = true) || content.trimStart().startsWith("{")
}

/**
 * IDE-glue manifest reader: reads manifest fields through the IntelliJ VFS instead of direct
 * file IO, delegating parsing to [FlatpakManifestReader]. Like the JDK conveniences it is
 * forgiving — an unreadable or unparseable manifest degrades to null/empty so detection and
 * command building never crash on a bad file.
 */
object FlatpakManifestVfsReader {
    private val log = Log.getInstance(FlatpakManifestVfsReader::class.java)

    /**
     * Reads several fields from a Flatpak manifest file in a single VFS read/parse.
     *
     * @param file The VirtualFile of the Flatpak manifest
     * @param keys The manifest field names to extract
     * @return A map of key to field value; missing or unreadable keys map to null
     */
    fun readFields(
        file: VirtualFile,
        vararg keys: String,
    ): Map<String, String?> {
        val content =
            try {
                file.contentsToByteArray().toString(Charsets.UTF_8)
            } catch (e: Exception) {
                log.warn("Could not read Flatpak manifest via VFS: ${file.path}", e)
                return emptyMap()
            }
        return FlatpakManifestReader.parseFieldsForgiving(content, file.name, *keys)
    }

    /**
     * Path-based convenience for IDE glue: resolves [path] through the VFS
     * ([LocalFileSystem]) so reads go through the platform's file layer.
     *
     * When the platform is unavailable (headless plain-JUnit tests, standalone
     * tooling) or the file cannot be resolved through the VFS, falls back to the
     * pure-JDK [FlatpakManifestReader] so the call still works.
     *
     * @param project the project the path belongs to (used for VFS resolution)
     * @param path the absolute path to the Flatpak manifest
     * @param keys The manifest field names to extract
     * @return A map of key to field value; missing or unreadable keys map to null
     */
    fun readFields(
        project: Project,
        path: String,
        vararg keys: String,
    ): Map<String, String?> {
        val file = findInVfs(path)
        return if (file != null) {
            readFields(file, *keys)
        } else {
            FlatpakManifestReader.readFields(path, *keys)
        }
    }

    /**
     * Reads the application ID from a Flatpak manifest file.
     *
     * @param file The VirtualFile of the Flatpak manifest
     * @return The application ID if found, or null if the file cannot be read or parsed
     */
    fun readAppId(file: VirtualFile): String? =
        FlatpakManifestReader.pickAppId(readFields(file, "app-id", "id"))

    /**
     * Reads the application ID via [readFields]' path-based resolution.
     */
    fun readAppId(
        project: Project,
        path: String,
    ): String? = FlatpakManifestReader.pickAppId(readFields(project, path, "app-id", "id"))

    /**
     * Reads the `command` field from a Flatpak manifest file.
     *
     * @param project the project the path belongs to (used for VFS resolution)
     * @param path the absolute path to the Flatpak manifest
     * @return The command run inside the sandbox if found, or null if it cannot be read
     */
    fun readCommand(
        project: Project,
        path: String,
    ): String? = readFields(project, path, "command")["command"]

    private fun findInVfs(path: String): VirtualFile? =
        try {
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(path))
        } catch (e: Throwable) {
            log.debug("VFS unavailable; falling back to JDK read for: $path")
            null
        }
}
