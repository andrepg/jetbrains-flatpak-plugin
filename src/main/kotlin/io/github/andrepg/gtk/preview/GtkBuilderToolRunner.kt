package io.github.andrepg.gtk.preview

import io.github.andrepg.gtk.schema.SdkHint
import io.github.andrepg.gtk.schema.locator.GirSdkLocator
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

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
        val stderr: String,
    ) {
        val ok: Boolean get() = exitCode == 0 && pngFile != null && pngFile.isFile
    }

    /** Resolves the SDK branch to use for [sdkHint]. */
    fun resolveBranch(sdkHint: SdkHint?, flatpakBinary: String): BranchResolution {
        if (sdkHint == null) return BranchResolution.NotFound

        val stdout = runProcess(listOf(flatpakBinary, "list", "--runtime", "--columns=application,branch,installation"))
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

        val result = runProcess(command, timeoutMs)
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

        val result = runProcess(command, timeoutMs)
        return RenderResult(
            exitCode = result?.exitCode ?: 1,
            pngFile = if (result?.exitCode == 0) outPng else null,
            stderr = result?.stderr ?: "",
        )
    }

    /** Runs a process and captures stdout/stderr separately. */
    private fun runProcess(command: List<String>, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ProcessResult? {
        val process = try {
            ProcessBuilder(command).start()
        } catch (e: IOException) {
            return null
        }

        val stdoutReader = process.inputStream.bufferedReader()
        val stderrReader = process.errorStream.bufferedReader()
        val stdout = Thread { stdoutReader.readText() }.also { it.isDaemon = true; it.start() }
        val stderr = Thread { stderrReader.readText() }.also { it.isDaemon = true; it.start() }

        return try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                null
            } else {
                ProcessResult(process.exitValue(), stdout.join().toString(), stderr.join().toString())
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Result of a process execution. */
    private data class ProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

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
