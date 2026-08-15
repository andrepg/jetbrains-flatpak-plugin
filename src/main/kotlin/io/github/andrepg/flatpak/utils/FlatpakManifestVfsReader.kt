package io.github.andrepg.flatpak.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.github.andrepg.flatpak.exception.FlatpakManifestException
import io.github.andrepg.shared.log.Log
import java.io.File

/**
 * IDE-glue manifest reader: reads manifest fields through the IntelliJ VFS
 * instead of direct file IO.
 *
 * [FlatpakManifestReader] is the pure-JDK parser (used by tooling and hermetic
 * tests); this class adapts it to [VirtualFile]. Like the JDK overloads it is
 * forgiving — an unreadable or unparseable manifest degrades to null/empty so
 * detection and command building never crash on a bad file.
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
        return try {
            FlatpakManifestReader.parseFields(content, file.name, *keys)
        } catch (e: FlatpakManifestException) {
            log.warn(e.message ?: "Could not parse Flatpak manifest: ${file.path}", e)
            emptyMap()
        }
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
    fun readAppId(file: VirtualFile): String? {
        val fields = readFields(file, "app-id", "id")
        return fields["app-id"] ?: fields["id"]
    }

    /**
     * Reads the application ID via [readFields]' path-based resolution.
     */
    fun readAppId(
        project: Project,
        path: String,
    ): String? {
        val fields = readFields(project, path, "app-id", "id")
        return fields["app-id"] ?: fields["id"]
    }

    /**
     * Reads the `command` field from a Flatpak manifest file.
     *
     * @param file The VirtualFile of the Flatpak manifest
     * @return The command run inside the sandbox if found, or null if it cannot be read
     */
    fun readCommand(file: VirtualFile): String? = readFields(file, "command")["command"]

    /**
     * Reads the `command` field via [readFields]' path-based resolution.
     */
    fun readCommand(
        project: Project,
        path: String,
    ): String? = readFields(project, path, "command")["command"]

    /**
     * Reads the `sdk` field from a Flatpak manifest file.
     *
     * @param file The VirtualFile of the Flatpak manifest
     * @return The SDK app-id if found, or null if it cannot be read
     */
    fun readSdk(file: VirtualFile): String? = readFields(file, "sdk")["sdk"]

    /**
     * Reads the `runtime` field from a Flatpak manifest file.
     *
     * @param file The VirtualFile of the Flatpak manifest
     * @return The runtime app-id if found, or null if it cannot be read
     */
    fun readRuntime(file: VirtualFile): String? = readFields(file, "runtime")["runtime"]

    private fun findInVfs(path: String): VirtualFile? =
        try {
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(path))
        } catch (e: Throwable) {
            log.debug("VFS unavailable; falling back to JDK read for: $path")
            null
        }
}
