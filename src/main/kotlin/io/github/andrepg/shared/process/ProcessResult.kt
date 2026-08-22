package io.github.andrepg.shared.process

/** Result of a process execution. */
data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val succeeded: Boolean get() = exitCode == 0
}
