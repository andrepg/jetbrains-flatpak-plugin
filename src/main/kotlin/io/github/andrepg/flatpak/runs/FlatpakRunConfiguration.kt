package io.github.andrepg.flatpak.runs

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.enums.FlatpakCommand
import io.github.andrepg.flatpak.enums.FlatpakRunAttributes
import org.jdom.Element

class FlatpakRunConfiguration(
    project: Project,
    factory: FlatpakConfigurationFactory,
    name: String
) : RunConfigurationBase<Any>(project, factory, name) {

    var command: FlatpakCommand = FlatpakCommand.BUILD
    var manifestPath: String = FlatpakRunSettings.DEFAULT_MANIFEST
    var buildDir: String = FlatpakRunSettings.DEFAULT_OUTPUT

    /**
     * Get current plugin status
     */
    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ) =
        FlatpakRunState(environment, this)

    /**
     * Write external values to IDE
     */
    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute(FlatpakRunAttributes.command.toString(), command.name)
        element.setAttribute(FlatpakRunAttributes.manifest.toString(), manifestPath)
        element.setAttribute(FlatpakRunAttributes.buildDir.toString(), buildDir)
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = FlatpakRunSettingsEditor()

    /**
     * Read external values from IDE
     */
    override fun readExternal(element: Element) {
        super.readExternal(element)

        command = FlatpakCommand.valueOf(
            getAttributeValue(element,
                FlatpakRunAttributes.command,
                FlatpakCommand.BUILD.toString()
            )
        )

        manifestPath = getAttributeValue(element,
            FlatpakRunAttributes.manifest,
            FlatpakRunSettings.DEFAULT_MANIFEST
        )

        buildDir = getAttributeValue(element,
            FlatpakRunAttributes.buildDir,
            FlatpakRunSettings.DEFAULT_OUTPUT
        )
    }

    /**
     * Get current element attribute, or return a default value
     */
    fun getAttributeValue(
        element: Element,
        attribute: FlatpakRunAttributes,
        default: String
    ): String {
        return element.getAttributeValue(attribute.toString(), default)
    }
}