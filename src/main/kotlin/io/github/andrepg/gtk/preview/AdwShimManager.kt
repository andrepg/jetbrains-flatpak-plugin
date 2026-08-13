package io.github.andrepg.gtk.preview

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Manages the Adwaita shim compilation and caching.
 *
 * JDK-only, no IntelliJ imports. Per-branch, idempotent: writes the .c file,
 * compiles the .so, and caches it in the work directory.
 */
class AdwShimManager(
    private val workDir: File,
    private val flatpakBinary: String,
) {
    /** The shim source code. */
    private val shimSource = """
#include <adwaita.h>

__attribute__((constructor))
static void adw_shim_init(void) {
    adw_init();
}
"""

    /** The shim source file for [branch]. */
    private fun shimSourceFile(branch: String): File = File(workDir, "adw-shim-$branch.c")

    /** The compiled shim file for [branch]. */
    fun shimFile(branch: String): File = File(workDir, "adw-shim-$branch.so")

    /**
     * Ensures the shim is compiled for [branch].
     *
     * @return the compiled shim file, or null if compilation failed.
     */
    @Synchronized
    fun ensureShim(sdkAppId: String, branch: String): File? {
        val shim = shimFile(branch)
        if (shim.isFile) return shim

        val source = shimSourceFile(branch).apply { writeText(shimSource) }
        val cflags = runProcess(listOf(flatpakBinary, "run", "--command=pkg-config", "--filesystem=host", "$sdkAppId//$branch", "--cflags", "libadwaita-1"))
            ?.stdout
            ?: return null
        val libs = runProcess(listOf(flatpakBinary, "run", "--command=pkg-config", "--filesystem=host", "$sdkAppId//$branch", "--libs", "libadwaita-1"))
            ?.stdout
            ?: return null

        val command = listOf(
            flatpakBinary,
            "run",
            "--command=cc",
            "--filesystem=host",
            "$sdkAppId//$branch",
            "-shared",
            "-fPIC",
            *cflags.split(" ").toTypedArray(),
            source.absolutePath,
            "-o",
            shim.absolutePath,
            *libs.split(" ").toTypedArray(),
        )

        val result = runProcess(command)
        return if (result?.exitCode == 0 && shim.isFile) shim else null
    }

    /** Runs a process and captures stdout/stderr separately. */
    private fun runProcess(command: List<String>, timeoutMs: Long = 120_000L): ProcessResult? {
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
        } catch (_: Exception) {
            null
        }
    }

    /** Result of a process execution. */
    private data class ProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}
