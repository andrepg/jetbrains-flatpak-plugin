package io.github.andrepg.shared.sentry

import io.github.andrepg.shared.log.Log

/**
 * Shields callers from any failure inside the optional Sentry client stack,
 * most notably [NoClassDefFoundError] when the plugin runs without its bundled
 * libraries (e.g. the bare plugin jar was installed instead of the
 * distribution ZIP).
 *
 * Error reporting is opt-in: a missing or broken SDK must degrade to
 * "reporting off" and never break IDE startup or settings applies.
 */
object SentryGuard {
    private val log = Log.getInstance(SentryGuard::class.java)

    /**
     * Runs [block], swallowing and logging every [Throwable] (including JVM
     * linkage errors, which are `Error`s rather than exceptions). Only use for
     * optional diagnostics that must never take the host down.
     */
    fun run(
        label: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: Throwable) {
            log.warn("$label failed; error reporting stays disabled", e)
        }
    }
}
