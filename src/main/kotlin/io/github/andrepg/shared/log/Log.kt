package io.github.andrepg.shared.log

import java.util.logging.Level
import java.util.logging.Logger as JdkLogger

/**
 * JDK-only logging facade used across the plugin.
 *
 * Wraps [java.util.logging.Logger] so that classes that must stay free of
 * IntelliJ imports (the JDK-only GTK schema/preview cores, which also run from
 * standalone tooling) log through the same channel that lands in `idea.log`.
 * The IntelliJ Platform's own logging is JUL-backed since 2022.1, so records
 * made here are captured by the IDE log (`Help | Show Log`) and are filtered
 * with `Help | Diagnostic Tools | Debug Log Settings` using the
 * `io.github.andrepg.*` categories.
 *
 * A [LogListener] can be attached (e.g. the Sentry breadcrumb bridge) to
 * observe every recorded event. Listeners must be fast and are isolated so a
 * misbehaving one never breaks the caller.
 */
class Log private constructor(private val jdk: JdkLogger) {

    /** Logger category, typically the owning class name. */
    val category: String get() = jdk.name

    val isDebugEnabled: Boolean get() = jdk.isLoggable(Level.FINE)

    fun info(message: String, throwable: Throwable? = null) = log(Level.INFO, message, throwable)

    fun warn(message: String, throwable: Throwable? = null) = log(Level.WARNING, message, throwable)

    fun error(message: String, throwable: Throwable? = null) = log(Level.SEVERE, message, throwable)

    fun debug(message: String, throwable: Throwable? = null) = log(Level.FINE, message, throwable)

    private fun log(level: Level, message: String, throwable: Throwable?) {
        if (!jdk.isLoggable(level)) return
        jdk.log(level, message, throwable)

        val l = listener ?: return
        try {
            l.onLog(category, level, message, throwable)
        } catch (e: Exception) {
            // A misbehaving listener must never break the caller.
            if (jdk.isLoggable(Level.FINE)) {
                jdk.log(Level.FINE, "LogListener failed for $category", e)
            }
        }
    }

    companion object {
        @Volatile
        var listener: LogListener? = null

        fun getInstance(clazz: Class<*>): Log = getInstance(clazz.name)

        fun getInstance(category: String): Log = Log(JdkLogger.getLogger(category))
    }
}

