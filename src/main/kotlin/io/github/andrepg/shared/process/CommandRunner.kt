package io.github.andrepg.shared.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler

/**
 * Injectable process-runner seam so the flatpak-integrated tooling can be
 * tested hermetically without a real flatpak/SDK installed. The default
 * [DefaultProcessRunner] runs through the IDE's process API, so every
 * synchronous CLI caller shares the platform's process lifecycle instead of
 * a hand-rolled JDK process stack.
 */
fun interface CommandRunner {
    fun run(
        command: List<String>,
        timeoutMs: Long,
    ): ProcessResult?
}

/**
 * [CapturingProcessHandler]-backed implementation used in production.
 *
 * Captures stdout/stderr separately and destroys the process when [timeoutMs]
 * elapses. Returns null when the process cannot be started or times out (the
 * nullable contract every caller relies on), otherwise the exit code and
 * captured output. Runs on the calling thread; safe to use from pooled and
 * background threads. Callers that only care about the exit code may ignore
 * the output.
 */
val DefaultProcessRunner =
    CommandRunner { command, timeoutMs ->
        val output =
            try {
                CapturingProcessHandler(GeneralCommandLine(command)).runProcess(timeoutMs.toInt())
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return@CommandRunner null
            } catch (_: Exception) {
                return@CommandRunner null
            }
        if (output.isTimeout) null else ProcessResult(output.exitCode, output.stdout, output.stderr)
    }