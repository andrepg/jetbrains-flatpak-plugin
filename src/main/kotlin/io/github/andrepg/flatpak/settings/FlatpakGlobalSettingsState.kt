package io.github.andrepg.flatpak.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import io.github.andrepg.flatpak.settings.DefaultFlatpakPaths

@Service
class FlatpakGlobalSettingsState : BaseState() {
    var flatpakBinaryPath: String? by string(DefaultFlatpakPaths.MAIN_BINARY)
    var flatpakBuilderBinaryPath: String? by string(DefaultFlatpakPaths.BUILDER_BINARY)
}
