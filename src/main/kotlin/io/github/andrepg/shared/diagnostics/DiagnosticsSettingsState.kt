package io.github.andrepg.shared.diagnostics

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service

/**
 * Persisted Diagnostics toggle state (opt-in Sentry error reporting + verbose
 * plugin logging), kept out of the Flatpak binary settings so the shared
 * diagnostics stack never depends on a domain package.
 */
@Service
class DiagnosticsSettingsState : BaseState() {
    /** Opt-in anonymous error reporting via Sentry (off by default). */
    var sentryEnabled: Boolean by property(false)

    /** Verbose `io.github.andrepg.*` logging into the IDE log (off by default). */
    var debugLoggingEnabled: Boolean by property(false)
}