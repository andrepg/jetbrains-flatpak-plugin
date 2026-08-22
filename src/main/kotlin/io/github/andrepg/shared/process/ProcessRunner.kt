package io.github.andrepg.shared.process

import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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
                reap(process)
                null
            } else {
                ProcessResult(
                    exitCode = process.exitValue(),
                    stdout = drain(stdoutFuture),
                    stderr = drain(stderrFuture),
                )
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            reap(process)
            null
        } catch (e: ExecutionException) {
            process.destroyForcibly()
            reap(process)
            null
        } catch (e: CompletionException) {
            // Unchecked counterpart of ExecutionException, thrown by the join() fallback in drain().
            process.destroyForcibly()
            reap(process)
            null
        }
    }

    /**
     * Waits for the stream reader to finish. Process death guarantees EOF on both pipes,
     * so a drain timeout only means the output was huge; fall back to an unbounded join
     * rather than discarding an already-successful exit.
     */
    private fun drain(future: CompletableFuture<String>): String =
        try {
            future.get(STREAM_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.join()
        }

    /** Best-effort wait so the reaper thread does not linger after a forced kill. */
    private fun reap(process: Process) {
        try {
            process.waitFor(STREAM_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
