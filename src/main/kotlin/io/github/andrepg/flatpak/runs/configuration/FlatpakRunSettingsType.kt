package io.github.andrepg.flatpak.runs.configuration

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.icons.AllIcons
import io.github.andrepg.shared.Localization


class FlatpakRunSettingsType: ConfigurationTypeBase(
    id = "flatpak",
    displayName = Localization.message("runs.configuration.displayName"),
    description = Localization.message("runs.configuration.description"),
    icon = AllIcons.RunConfigurations.Application
) {
    init {
        addFactory(FlatpakRunSettingsFactory(this))
    }
}