package io.github.andrepg.shared.process

import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Minimal JDK-only process runner used by the flatpak-integrated tooling.
 *
 * Starts a process with the given command line, reads stdout/stderr on separate
 * threads (no deadlock risk), and applies a timeout. All outputs are captured
 * in memory; results are immutable.
 */
object ProcessRunner {
    private const val DEFAULT_TIMEOUT_MS = 120_000L
    private const val STREAM_DRAIN_TIMEOUT_SECONDS = 5L

    /** Runs [command] and captures stdout/stderr separately. */
    fun run(
        command: List<String>,
        workDir: File? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        env: Map<String, String> = emptyMap(),
    ): ProcessResult? {
        val process =
            try {
                ProcessBuilder(command).apply {
                    workDir?.let { directory(it) }
                    env.forEach { (key, value) -> environment()[key] = value }
                }.start()
            } catch (e: IOException) {
                return null
            }

        val stdoutFuture = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
        val stderrFuture = CompletableFuture.supplyAsync { process.errorStream.bufferedReader().readText() }

        return try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                null
            } else {
                ProcessResult(
                    exitCode = process.exitValue(),
                    stdout = stdoutFuture.get(STREAM_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    stderr = stderrFuture.get(STREAM_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            null
        }
    }

    /** Result of a process execution. */
    data class ProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        val succeeded: Boolean get() = exitCode == 0
    }
}

/**
 * Injectable process-runner seam so the flatpak-integrated tooling can be tested
 * hermetically without a real flatpak/SDK installed. The default
 * [DefaultProcessRunner] shells out to [ProcessRunner].
 */
fun interface CommandRunner {
    fun run(
        command: List<String>,
        timeoutMs: Long,
    ): ProcessRunner.ProcessResult?
}

/** Real [ProcessRunner]-backed implementation used in production. */
val DefaultProcessRunner =
    CommandRunner { command, timeoutMs ->
        ProcessRunner.run(command, timeoutMs = timeoutMs)
    }
