package io.github.andrepg.flatpak.runs

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import io.github.andrepg.shared.Localization

/**
 * Run configuration type registering the "Flatpak" entry in the Run/Debug configurations dialog.
 */
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

/**
 * Factory that creates template Flatpak run configurations for this configuration type.
 */
class FlatpakConfigurationFactory(type: FlatpakRunConfigurationType) :
    ConfigurationFactory(type) {

    /**
     * Creates a new template configuration for the given project.
     *
     * @param project the project the configuration belongs to
     * @return the created [FlatpakRunConfiguration]
     */
    override fun createTemplateConfiguration(project: Project) =
        FlatpakRunConfiguration(project, this, "FlatpakRunConfiguration")

    /**
     * @return the unique identifier of this factory
     */
    override fun getId(): String = "FlatpakFactory"
}