package io.github.andrepg.flatpak.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service

@Service
class FlatpakGlobalSettingsState : BaseState() {
    var flatpakBinaryPath: String? by string(DefaultFlatpakPaths.MAIN_BINARY)
    var flatpakBuilderBinaryPath: String? by string(DefaultFlatpakPaths.BUILDER_BINARY)

    /** Opt-in anonymous error reporting via Sentry (off by default). */
    var sentryEnabled: Boolean by property(false)

    /** Verbose `io.github.andrepg.*` logging into the IDE log (off by default). */
    var debugLoggingEnabled: Boolean by property(false)
}
