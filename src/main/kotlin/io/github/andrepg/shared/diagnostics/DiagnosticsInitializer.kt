package io.github.andrepg.shared.diagnostics

import com.intellij.ide.AppLifecycleListener
import io.github.andrepg.shared.log.Log
import io.github.andrepg.shared.log.LogConfiguration
import io.github.andrepg.shared.sentry.SentryGuard
import io.github.andrepg.shared.sentry.SentryInitializer
import io.github.andrepg.shared.sentry.SentryOptInNotification

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

    /**
     * Defines if the Sentry logging is enabled based on User's preference
     * configured at IDE settings
     */
    private val enableSentry = DiagnosticsSettings.sentryEnabled

    /**
     * Holds if user opted out from Sentry definitely by disabling
     * and forgetting the baloon notification shwon at startup
     */
    private val userDisabledNotification = DiagnosticsSettings.sentryInvitationForgotten

    /**
     * Defines if your Debug logging is enabled based on current
     * build flags - this should almost never be true in production
     */
    private val enableDebug = DiagnosticsSettings.debugLoggingEnabled

    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        applyRuntimeConfiguration()
    }

    /**
     * Check current User's Sentry reporting preference and if notification
     * baloon was already dismissed and forgotten. If opt-out was not
     * forgotten yet, we will fire a notification asking user to opt-in.
     */
    fun applyRuntimeConfiguration() {
        LogConfiguration.setDebugEnabled(enableDebug || LogConfiguration.isDebugRequested())

        if (enableSentry) {
            SentryGuard.run("Sentry initialization") { SentryInitializer.reconfigure() }
        } else if (!userDisabledNotification) {
            SentryOptInNotification().notify(null)
        }

        log.info("Initializing diagnostics module [Sentry: $enableSentry | Debug: $enableDebug]")
    }
}
