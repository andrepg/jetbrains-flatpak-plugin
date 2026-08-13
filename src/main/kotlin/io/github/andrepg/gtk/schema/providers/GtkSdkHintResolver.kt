package io.github.andrepg.gtk.schema.providers

import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.detection.FlatpakProjectDetector
import io.github.andrepg.flatpak.utils.FlatpakManifestReader
import io.github.andrepg.gtk.schema.SdkHint

/**
 * Resolves the GNOME SDK hint from a project's Flatpak manifests.
 *
 * IDE glue: imports the Flatpak domain for the SDK hint only.
 */
object GtkSdkHintResolver {
    /**
     * Derives the SDK hint from the project's Flatpak manifests: the first
     * manifest declaring a GNOME `sdk`/`runtime` (e.g. `org.gnome.Sdk//50`)
     * drives the schema branch; non-GNOME or manifest-less projects get null.
     */
    fun resolve(project: Project): SdkHint? {
        for ((file, _) in FlatpakProjectDetector.findManifests(project)) {
            val candidate = FlatpakManifestReader.readSdk(file.path)
                ?: FlatpakManifestReader.readRuntime(file.path)
                ?: continue
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
