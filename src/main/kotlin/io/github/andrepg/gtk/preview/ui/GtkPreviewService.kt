package io.github.andrepg.gtk.preview.ui

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.andrepg.flatpak.settings.FlatpakSettings
import io.github.andrepg.gtk.preview.AdwShimManager
import io.github.andrepg.gtk.preview.GtkBuilderToolRunner
import io.github.andrepg.gtk.schema.SdkHint
import io.github.andrepg.gtk.schema.providers.GtkSdkHintResolver
import io.github.andrepg.shared.FeatureFlags
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.log.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Composition root for the GTK preview feature.
 *
 * IDE glue: holds the flatpak binary path, shim manager, and validation cache.
 * Provides methods to resolve SDK, validate, and render.
 */
@Service(Service.Level.PROJECT)
class GtkPreviewService(private val project: Project) {
    val configDir: File = PathManager.getConfigDir().resolve("flatpak-preview").toFile()
    val flatpakBinary: String get() = FlatpakSettings.flatpakBinary
    private val shimManager by lazy { AdwShimManager(configDir, flatpakBinary) }
    private val cache = ConcurrentHashMap<String, ValidationResult>()
    private val log = Log.getInstance(GtkPreviewService::class.java)

    companion object {
        private const val MAX_CACHE_ENTRIES = 50
    }

    /**
     * Resolves the GNOME SDK hint for the project.
     *
     * @return the SDK hint, or null if no GNOME SDK is declared.
     */
    fun resolveSdk(): SdkHint? {
        if (!isGtkPreviewEnabled()) return null
        return GtkSdkHintResolver.resolve(project)
    }

    private fun isGtkPreviewEnabled(): Boolean =
        FeatureFlags.getBoolean(FeatureFlags.FEATURE_FLAG_ENABLE_GTK_PREVIEW)

    /**
     * Validates a .ui file with caching.
     *
     * Only successful (gate-passing) validations are cached: failures depend on
     * the Adwaita shim being compiled or the SDK being installed, both of which
     * can change between attempts, so caching them would surface stale errors
     * forever (e.g. "Adwaita types unsupported" recorded before the shim was built).
     *
     * @return the validation result, including branch resolution and shim status.
     */
    fun validate(file: VirtualFile): ValidationResult {
        if (!isGtkPreviewEnabled()) {
            return ValidationResult(null, null, false, "", Localization.message("preview.notification.disabled"), null)
        }
        val key = "${file.path}#${file.timeStamp}"
        cache[key]?.let { return it }

        val hint = resolveSdk()
        if (hint == null) return ValidationResult(null, null, false, "", Localization.message("preview.notification.no-sdk"), null)

        val resolution = GtkBuilderToolRunner.resolveBranch(hint, flatpakBinary)
        val branch = when (resolution) {
            is GtkBuilderToolRunner.BranchResolution.Installed -> resolution.branch
            is GtkBuilderToolRunner.BranchResolution.BranchNotInstalled -> return ValidationResult(hint.sdkAppId, null, false, "", installHint(hint.sdkAppId, resolution.requestedBranch), null)
            GtkBuilderToolRunner.BranchResolution.NotFound -> return ValidationResult(hint.sdkAppId, null, false, "", installHint(hint.sdkAppId, hint.branch), null)
        }

        val shim = shimManager.ensureShim(hint.sdkAppId, branch)
        val result = GtkBuilderToolRunner.validate(File(file.path), hint.sdkAppId, branch, flatpakBinary, shim?.absolutePath)
        val validation = ValidationResult(hint.sdkAppId, branch, result.ok, result.stderr, null, shim?.absolutePath)
        log.debug(
            "Validated ${file.path}: sdk=${hint.sdkAppId}//$branch, shim=${shim?.absolutePath ?: "none"}, " +
                "ok=${result.ok}${if (!result.ok) " (${result.stderr.take(200)})" else ""}"
        )
        return if (validation.gatePassed) cacheAndReturn(key, validation) else validation
    }

    /**
     * Renders a .ui file to PNG.
     *
     * @return the render result, including the PNG file or null if rendering failed.
     */
    fun render(file: VirtualFile, branch: String, ldPreload: File?): GtkBuilderToolRunner.RenderResult {
        if (!isGtkPreviewEnabled()) {
            return GtkBuilderToolRunner.RenderResult(0, null)
        }
        val outPng = File(configDir, "preview-${branch}-${file.nameWithoutExtension}.png")
        val result = GtkBuilderToolRunner.render(
            File(file.path), outPng, resolveSdk()?.sdkAppId ?: "org.gnome.Sdk", branch, flatpakBinary, ldPreload?.absolutePath
        )
        log.debug("Rendered ${file.path} to $outPng (exit=${result.exitCode}, png=${result.pngFile != null})")
        return result
    }

    /**
     * Ensures the Adwaita shim is compiled for the given branch.
     *
     * @return the shim file, or null if compilation failed.
     */
    fun ensureShim(branch: String): File? {
        if (!isGtkPreviewEnabled()) return null
        return shimManager.shimFile(branch).takeIf { it.isFile }
    }

    private fun cacheAndReturn(key: String, result: ValidationResult): ValidationResult {
        if (cache.size >= MAX_CACHE_ENTRIES && !cache.containsKey(key)) {
            cache.keys.firstOrNull()?.let { cache.remove(it) }
        }
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
