package io.github.andrepg.shared.log

import java.util.logging.Level

/**
 * Observes every event recorded through [Log].
 *
 * Implementations must not block and must never throw: exceptions thrown by a
 * listener are swallowed so the logging caller is never affected.
 */
fun interface LogListener {
    fun onLog(category: String, level: Level, message: String, throwable: Throwable?)
}