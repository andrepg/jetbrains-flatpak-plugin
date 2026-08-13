package io.github.andrepg.flatpak.detection

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import io.github.andrepg.flatpak.utils.FlatpakManifestReader

/**
 * Pure service that detects Flatpak manifests in a project.
 *
 * Lives in its own domain with no dependency on run configuration or UI code; the `runs`
 * domain (producer, configurator, project opener) consumes it for every detection concern.
 */
object FlatpakProjectDetector {

    /** Reverse-DNS names such as `org.example.app.json` (at least three segments). */
    private val reverseDnsNameRegex =
        Regex("^[a-z0-9]+(\\.[a-z0-9]+){2,}\\.(json|ya?ml)$")

    /** Generic names starting with `flatpak`/`manifest`, e.g. `flatpak.json` or `flatpak-manifest.yml`. */
    private val commonNameRegex =
        Regex("^(flatpak|manifest).*\\.(json|ya?ml)$")

    private val excludedDirectoryNames = setOf(
        ".git",
        "build",
        "_build",
        "out",
        "node_modules",
        "target",
        ".flatpak-builder"
    )

    /**
     * Returns the app-id when [file] looks like a Flatpak manifest, null otherwise.
     *
     * The filename is matched before any content is read, so arbitrary files are never parsed.
     */
    fun isFlatpakManifest(file: VirtualFile): String? =
        readAppIdFromCandidate(file.name, file.path)

    /**
     * Filename heuristic shared by [isFlatpakManifest] and the pure-JUnit tests.
     *
     * Accepts reverse-DNS names such as `org.example.app.json` (at least three segments) and
     * generic names starting with `flatpak`/`manifest`, restricted to {json, yaml, yml} extensions.
     */
    internal fun isCandidateName(fileName: String): Boolean {
        val name = fileName.lowercase()
        return name.matches(reverseDnsNameRegex) || name.matches(commonNameRegex)
    }

    /**
     * Applies the [isCandidateName] gate and delegates content parsing to [FlatpakManifestReader].
     */
    internal fun readAppIdFromCandidate(fileName: String, path: String): String? {
        if (!isCandidateName(fileName)) return null
        return FlatpakManifestReader.readAppId(path)
    }

    /**
     * Recursively walks the project content roots, skipping excluded directories, and returns
     * every manifest found as a (file, app-id) pair. File content is only read after the
     * filename heuristic matches.
     */
    fun findManifests(project: Project): List<Pair<VirtualFile, String>> {
        val manifests = mutableListOf<Pair<VirtualFile, String>>()
        for (root in ProjectRootManager.getInstance(project).contentRoots) {
            VfsUtilCore.visitChildrenRecursively(
                root,
                object : VirtualFileVisitor<Any>(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (file.isDirectory) {
                            return file.name !in excludedDirectoryNames
                        }
                        isFlatpakManifest(file)?.let { manifests.add(file to it) }
                        return true
                    }
                }
            )
        }
        return manifests
    }

    /**
     * @return true when at least one Flatpak manifest is present in the project content roots.
     */
    fun isFlatpakProject(project: Project): Boolean = findManifests(project).isNotEmpty()
}
