package io.github.andrepg.gtk.preview.ui

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.andrepg.flatpak.settings.DefaultFlatpakPaths
import io.github.andrepg.gtk.preview.AdwShimManager
import io.github.andrepg.gtk.preview.GtkBuilderToolRunner
import io.github.andrepg.gtk.schema.SdkHint
import io.github.andrepg.gtk.schema.providers.GtkSdkHintResolver
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Composition root for the GTK preview feature.
 *
 * IDE glue: holds the flatpak binary path, shim manager, and validation cache.
 * Provides methods to resolve SDK, validate, and render.
 */
class GtkPreviewService(private val project: Project) {
    val configDir: File = PathManager.getConfigDir().resolve("flatpak-preview").toFile()
    val flatpakBinary: String = DefaultFlatpakPaths.MAIN_BINARY
    private val shimManager by lazy { AdwShimManager(configDir, flatpakBinary) }
    private val cache = ConcurrentHashMap<String, ValidationResult>()

    /**
     * Resolves the GNOME SDK hint for the project.
     *
     * @return the SDK hint, or null if no GNOME SDK is declared.
     */
    fun resolveSdk(): SdkHint? = GtkSdkHintResolver.resolve(project)

    /**
     * Validates a .ui file with caching.
     *
     * @return the validation result, including branch resolution and shim status.
     */
    fun validate(file: VirtualFile): ValidationResult {
        val key = "${file.path}#${file.timeStamp}"
        cache[key]?.let { return it }

        val hint = resolveSdk()
        if (hint == null) return cacheAndReturn(key, ValidationResult(null, null, false, "", "No GNOME SDK declared in manifest", null))

        val resolution = GtkBuilderToolRunner.resolveBranch(hint, flatpakBinary)
        val branch = when (resolution) {
            is GtkBuilderToolRunner.BranchResolution.Installed -> resolution.branch
            is GtkBuilderToolRunner.BranchResolution.BranchNotInstalled -> return cacheAndReturn(key, ValidationResult(hint.sdkAppId, null, false, "", installHint(hint.sdkAppId, resolution.requestedBranch), null))
            GtkBuilderToolRunner.BranchResolution.NotFound -> return cacheAndReturn(key, ValidationResult(hint.sdkAppId, null, false, "", installHint(hint.sdkAppId, hint.branch), null))
        }

        val shim = shimManager.ensureShim(hint.sdkAppId, branch)
        val result = GtkBuilderToolRunner.validate(File(file.path), hint.sdkAppId, branch, flatpakBinary, shim?.absolutePath)
        return cacheAndReturn(key, ValidationResult(hint.sdkAppId, branch, result.ok, result.stderr, null, shim?.absolutePath))
    }

    /**
     * Renders a .ui file to PNG.
     *
     * @return the render result, including the PNG file or null if rendering failed.
     */
    fun render(file: VirtualFile, branch: String, ldPreload: File?): GtkBuilderToolRunner.RenderResult {
        val outPng = File(configDir, "preview-${branch}-${file.nameWithoutExtension}.png")
        return GtkBuilderToolRunner.render(File(file.path), outPng, resolveSdk()?.sdkAppId ?: "org.gnome.Sdk", branch, flatpakBinary, ldPreload?.absolutePath)
    }

    /**
     * Ensures the Adwaita shim is compiled for the given branch.
     *
     * @return the shim file, or null if compilation failed.
     */
    fun ensureShim(branch: String): File? = shimManager.shimFile(branch).takeIf { it.isFile }

    private fun cacheAndReturn(key: String, result: ValidationResult): ValidationResult {
        cache[key] = result
        return result
    }

    private fun installHint(sdkAppId: String, branch: String?): String {
        return if (branch != null) {
            "flatpak install $sdkAppId//$branch"
        } else {
            "flatpak install $sdkAppId"
        }
    }

    /** Result of a validation. */
    data class ValidationResult(
        val sdkAppId: String?,
        val branch: String?,
        val ok: Boolean,
        val stderr: String,
        val message: String?,      // non-null when preview unavailable (install hint / no sdk)
        val ldPreload: String?,    // shim path or null
    ) {
        val previewAvailable: Boolean get() = branch != null && message == null
        val gatePassed: Boolean get() = previewAvailable && ok && stderr.isBlank()
        val adwUnsupported: Boolean get() = branch != null && !ok && stderr.contains("Invalid object type")
    }
}
