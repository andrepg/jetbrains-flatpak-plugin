package io.github.andrepg.shared.diagnostics

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.components.service
import io.github.andrepg.flatpak.settings.FlatpakGlobalSettingsState
import io.github.andrepg.shared.log.Log
import io.github.andrepg.shared.log.LogConfiguration
import io.github.andrepg.shared.sentry.SentryGuard
import io.github.andrepg.shared.sentry.SentryInitializer

/**
 * Plugin-wide diagnostics bootstrap, wired as an
 * `com.intellij.ide.AppLifecycleListener` in `plugin.xml`.
 *
 * Runs once when the IDE main frame is created and applies the Diagnostics
 * settings: turns verbose `io.github.andrepg.*` logging on/off (see
 * [LogConfiguration]) and (re)starts the opt-in Sentry client (see
 * [SentryInitializer]). Also logs a single startup summary so a debug session
 * starts from a known state.
 *
 * [applyRuntimeConfiguration] is also called when the Diagnostics settings are
 * applied, so toggles take effect without restarting the IDE.
 */
class DiagnosticsInitializer : AppLifecycleListener {
    private val log = Log.getInstance(DiagnosticsInitializer::class.java)

    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        applyRuntimeConfiguration()
    }

    /** Applies the current debug-logging and Sentry settings. */
    fun applyRuntimeConfiguration() {
        val debugEnabled =
            LogConfiguration.isDebugRequested() ||
                service<FlatpakGlobalSettingsState>().debugLoggingEnabled
        LogConfiguration.setDebugEnabled(debugEnabled)

        var sentryActive = false
        SentryGuard.run("Sentry initialization") {
            SentryInitializer.reconfigure()
            sentryActive = SentryInitializer.isActive
        }

        log.info(
            "Flatpak DevTools initialized: debug logging=${if (debugEnabled) "on" else "off"}, " +
                "sentry error reporting=${if (sentryActive) "on" else "off"}",
        )
    }
}
