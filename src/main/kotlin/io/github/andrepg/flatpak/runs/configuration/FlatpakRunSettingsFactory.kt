package io.github.andrepg.flatpak.runs.configuration

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NonNls

class FlatpakRunSettingsFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): @NonNls String = RunConfiguration.DATA_KEY.name

    override fun getOptionsClass(): Class<out BaseState> = FlatpakRunSettingsAttributes::class.java

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return FlatpakRunSettings(project, this, id)
    }
}
