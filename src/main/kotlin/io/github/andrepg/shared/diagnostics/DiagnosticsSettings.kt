package io.github.andrepg.shared.diagnostics

import com.intellij.openapi.application.ApplicationManager

/**
 * Live accessor for the persisted Diagnostics toggles.
 *
 * Reads the [DiagnosticsSettingsState] application service, falling back to
 * `false` when no IDE application exists (headless unit tests).
 */
object DiagnosticsSettings {
    private fun state(): DiagnosticsSettingsState? =
        ApplicationManager.getApplication()?.getService(DiagnosticsSettingsState::class.java)

    /** Opt-in anonymous error reporting via Sentry. */
    val sentryEnabled: Boolean
        get() = state()?.sentryEnabled ?: false

    /** Verbose plugin logging into the IDE log. */
    val debugLoggingEnabled: Boolean
        get() = state()?.debugLoggingEnabled ?: false
}