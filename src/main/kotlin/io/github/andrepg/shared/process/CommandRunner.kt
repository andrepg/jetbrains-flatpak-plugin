package io.github.andrepg.shared.process

/**
 * Injectable process-runner seam so the flatpak-integrated tooling can be tested
 * hermetically without a real flatpak/SDK installed. The default
 * [DefaultProcessRunner] shells out to [ProcessRunner].
 */
fun interface CommandRunner {
    fun run(
        command: List<String>,
        timeoutMs: Long,
    ): ProcessResult?
}

/** Real [ProcessRunner]-backed implementation used in production. */
val DefaultProcessRunner =
    CommandRunner { command, timeoutMs ->
        ProcessRunner.run(command, timeoutMs = timeoutMs)
    }
