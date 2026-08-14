package io.github.andrepg.gtk.preview

import io.github.andrepg.gtk.schema.SdkHint
import io.github.andrepg.gtk.schema.locator.GirSdkLocator
import io.github.andrepg.shared.process.CommandRunner
import io.github.andrepg.shared.process.DefaultProcessRunner
import java.io.File

/**
 * Runs GtkBuilder tool commands inside the GNOME SDK via flatpak run.
 *
 * JDK-only, no IntelliJ imports. Handles command construction, process execution,
 * and branch resolution.
 */
object GtkBuilderToolRunner {
    private const val DEFAULT_TIMEOUT_MS = 120_000L

    /** Result of a validate command. */
    data class ValidationResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        /** Gate: exit 0 and no diagnostics on stderr. */
        val ok: Boolean get() = exitCode == 0
        val passesGate: Boolean get() = ok && stderr.isBlank()
    }

    /** Result of a render command. */
    data class RenderResult(
        val exitCode: Int,
        val pngFile: File?,
    ) {
        val ok: Boolean get() = exitCode == 0 && pngFile != null && pngFile.isFile
    }

    /** Resolves the SDK branch to use for [sdkHint]. */
    fun resolveBranch(
        sdkHint: SdkHint?,
        flatpakBinary: String,
        runner: CommandRunner = DefaultProcessRunner,
    ): BranchResolution {
        if (sdkHint == null) return BranchResolution.NotFound

        val stdout = runner.run(listOf(flatpakBinary, "list", "--runtime", "--columns=application,branch,installation"), DEFAULT_TIMEOUT_MS)
            ?.stdout
            ?: return BranchResolution.NotFound

        val rows = GirSdkLocator.parseRuntimeRows(stdout)
        val installed = rows.filter { it.appId == sdkHint.sdkAppId }
        if (installed.isEmpty()) return BranchResolution.NotFound

        sdkHint.branch?.let { pinned ->
            if (installed.any { it.branch == pinned }) return BranchResolution.Installed(pinned)
            return BranchResolution.BranchNotInstalled(pinned)
        }

        val best = GirSdkLocator.pickBranch(rows, sdkHint.sdkAppId, null)
        return if (best != null) BranchResolution.Installed(best) else BranchResolution.NotFound
    }

    /** Runs `gtk4-builder-tool validate` inside the GNOME SDK. */
    fun validate(
        uiFile: File,
        sdkAppId: String,
        branch: String,
        flatpakBinary: String,
        ldPreload: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        runner: CommandRunner = DefaultProcessRunner,
    ): ValidationResult {
        val command = mutableListOf(
            flatpakBinary,
            "run",
            "--command=gtk4-builder-tool",
            "--filesystem=host",
            "--socket=x11",
            "--socket=wayland",
            "--share=ipc",
            "$sdkAppId//$branch",
            "validate",
            uiFile.absolutePath,
        )
        ldPreload?.let { command.add(1, "--env=LD_PRELOAD=$it") }

        val result = runner.run(command, timeoutMs)
        return ValidationResult(
            exitCode = result?.exitCode ?: 1,
            stdout = result?.stdout ?: "",
            stderr = result?.stderr ?: "",
        )
    }

    /** Runs `gtk4-builder-tool render` inside the GNOME SDK. */
    fun render(
        uiFile: File,
        outPng: File,
        sdkAppId: String,
        branch: String,
        flatpakBinary: String,
        ldPreload: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        runner: CommandRunner = DefaultProcessRunner,
    ): RenderResult {
        val command = mutableListOf(
            flatpakBinary,
            "run",
            "--command=gtk4-builder-tool",
            "--filesystem=host",
            "--socket=x11",
            "--socket=wayland",
            "--share=ipc",
            "$sdkAppId//$branch",
            "render",
            "--force",
            uiFile.absolutePath,
            outPng.absolutePath,
        )
        ldPreload?.let { command.add(1, "--env=LD_PRELOAD=$it") }

        val result = runner.run(command, timeoutMs)
        return RenderResult(
            exitCode = result?.exitCode ?: 1,
            pngFile = if (result?.exitCode == 0) outPng else null,
//            stderr = result?.stderr ?: "",
        )
    }

    /** Result of branch resolution. */
    sealed interface BranchResolution {
        /** A usable installed branch. */
        data class Installed(val branch: String) : BranchResolution

        /** The manifest-pinned branch is not installed; other branches may exist. */
        data class BranchNotInstalled(val requestedBranch: String) : BranchResolution

        /** No rows at all (SDK absent or flatpak unavailable). */
        object NotFound : BranchResolution
    }
}
