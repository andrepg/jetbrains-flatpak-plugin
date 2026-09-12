package io.github.andrepg.flatpak.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service

/**
 * Persisted Flatpak binary settings. The diagnostics toggles live in
 * `io.github.andrepg.shared.diagnostics` so the shared stack stays independent
 * of this domain.
 */
@Service
class FlatpakGlobalSettingsState : BaseState() {
    var flatpakBinaryPath: String? by string(DefaultFlatpakPaths.MAIN_BINARY)
    var flatpakBuilderBinaryPath: String? by string(DefaultFlatpakPaths.BUILDER_BINARY)
}
