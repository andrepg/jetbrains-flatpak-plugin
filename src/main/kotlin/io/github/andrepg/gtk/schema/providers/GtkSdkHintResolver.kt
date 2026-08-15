package io.github.andrepg.gtk.schema.providers

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.andrepg.flatpak.detection.FlatpakProjectDetector
import io.github.andrepg.flatpak.utils.FlatpakManifestVfsReader
import io.github.andrepg.gtk.schema.SdkHint
import io.github.andrepg.shared.log.Log

/**
 * Resolves the GNOME SDK hint from a project's Flatpak manifests.
 *
 * IDE glue: imports the Flatpak domain for the SDK hint only.
 */
object GtkSdkHintResolver {

    private val log = Log.getInstance(GtkSdkHintResolver::class.java)

    /**
     * Derives the SDK hint from the project's Flatpak manifests: the first
     * manifest declaring a GNOME `sdk`/`runtime` (e.g. `org.gnome.Sdk//50`)
     * drives the schema branch; non-GNOME or manifest-less projects get null.
     */
    fun resolve(project: Project): SdkHint? {
        val hint = resolveFromManifests(FlatpakProjectDetector.findManifests(project))
        log.debug("Resolved GNOME SDK hint for ${project.name}: ${hint?.let { "${it.sdkAppId}//${it.branch}" } ?: "none"}")
        return hint
    }

    /**
     * Pure logic shared by [resolve]; takes the manifest list so the decision
     * can be tested without a running IDE project.
     */
    internal fun resolveFromManifests(manifests: List<Pair<VirtualFile, String>>): SdkHint? {
        for ((file, _) in manifests) {
            val fields = FlatpakManifestVfsReader.readFields(file, "sdk", "runtime")
            val candidate = fields["sdk"] ?: fields["runtime"] ?: continue
            val (appId, branch) = splitBranch(candidate)
            if (appId.startsWith("org.gnome.")) {
                return SdkHint(appId, branch)
            }
        }
        return null
    }

    private fun splitBranch(value: String): Pair<String, String?> {
        val index = value.indexOf("//")
        return if (index >= 0) {
            value.substring(0, index) to value.substring(index + 2).takeIf { it.isNotEmpty() }
        } else {
            value to null
        }
    }
}
