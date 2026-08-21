package io.github.andrepg.flatpak.settings

import com.intellij.openapi.application.ApplicationManager

/**
 * Live accessor for the configured Flatpak binaries.
 *
 * Reads the persisted [FlatpakGlobalSettingsState] application service, falling
 * back to the [DefaultFlatpakPaths] when the user has not customized anything.
 * Getters evaluate on every access so Settings changes apply to the next run.
 *
 * When no IDE application exists (plain unit tests), every getter falls back
 * to the documented defaults instead of throwing.
 */
object FlatpakSettings {
    /** The persisted state, or null outside the IDE (headless unit tests). */
    private fun state(): FlatpakGlobalSettingsState? =
        ApplicationManager.getApplication()?.getService(FlatpakGlobalSettingsState::class.java)

    /** The configured `flatpak` CLI binary. */
    val flatpakBinary: String
        get() = state()?.flatpakBinaryPath ?: DefaultFlatpakPaths.MAIN_BINARY

    /** The configured flatpak-builder invocation (binary or flatpak run id). */
    val builderBinary: String
        get() = state()?.flatpakBuilderBinaryPath ?: DefaultFlatpakPaths.BUILDER_BINARY

    /** Opt-in anonymous error reporting via Sentry. */
    val sentryEnabled: Boolean
        get() = state()?.sentryEnabled ?: false

    /** Verbose plugin logging into the IDE log. */
    val debugLoggingEnabled: Boolean
        get() = state()?.debugLoggingEnabled ?: false
}
