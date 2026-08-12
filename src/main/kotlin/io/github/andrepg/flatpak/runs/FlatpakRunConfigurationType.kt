package io.github.andrepg.flatpak.runs

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import io.github.andrepg.shared.Localization

class FlatpakRunConfigurationType :
    ConfigurationTypeBase(
        "FlatpakRunConfiguration",
        Localization.message("runs.configuration.displayName"),
        Localization.message("runs.configuration.description"),
        AllIcons.RunConfigurations.Application
    ) {

    init {
        addFactory(FlatpakConfigurationFactory(this))
    }
}

class FlatpakConfigurationFactory(type: FlatpakRunConfigurationType) :
    ConfigurationFactory(type) {

    override fun createTemplateConfiguration(project: Project) =
        FlatpakRunConfiguration(project, this, "FlatpakRunConfiguration")

    override fun getId(): String = "FlatpakFactory"
}