package io.github.andrepg.shared.log

import java.util.logging.Level
import java.util.logging.Logger as JdkLogger

/**
 * Global switch for verbose (FINE) logging across the whole `io.github.andrepg`
 * plugin namespace.
 *
 * JDK-only (no IntelliJ imports) so it can run from standalone tooling too. The
 * IntelliJ Platform's logging is JUL-backed, so raising the level of the
 * `io.github.andrepg` logger makes every [Log.debug] record land in the IDE log
 * (`Help | Show Log`), in the `io.github.andrepg.*` categories.
 *
 * Enable via the `flatpak.debug` system property or the *Diagnostics* settings
 * toggle (both feed [DiagnosticsInitializer]). The property is read directly so
 * it works before the IDE services are up; the settings toggle goes through
 * [setDebugEnabled].
 */
object LogConfiguration {
    /** System property that turns verbose plugin logging on (`-Dflatpak.debug=true`). */
    const val DEBUG_PROPERTY = "flatpak.debug"

    /** Common logger category prefix covering every plugin subsystem. */
    const val ROOT_CATEGORY = "io.github.andrepg"

    private val rootLogger: JdkLogger
        get() = JdkLogger.getLogger(ROOT_CATEGORY)

    /** Whether the user requested debug logging (property or settings). */
    fun isDebugRequested(): Boolean = System.getProperty(DEBUG_PROPERTY)?.toBoolean() == true

    /** Whether the FINE level is currently effective for the plugin namespace. */
    fun isDebugActive(): Boolean = (rootLogger.level?.intValue() ?: Level.INFO.intValue()) <= Level.FINE.intValue()

    /** Raises the plugin namespace level to FINE so all [Log.debug] calls emit. */
    fun setDebugEnabled(enabled: Boolean) {
        rootLogger.level = if (enabled) Level.FINE else null
        rootLogger.useParentHandlers = true
    }
}
