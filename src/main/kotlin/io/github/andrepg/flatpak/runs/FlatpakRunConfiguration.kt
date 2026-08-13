package io.github.andrepg.flatpak.runs

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.enums.FlatpakCommand
import io.github.andrepg.flatpak.runs.enums.FlatpakRunAttributes
import io.github.andrepg.flatpak.runs.ui.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.ui.FlatpakRunSettingsEditor
import org.jdom.Element

/**
 * Run configuration holding the state of a Flatpak build/run command.
 *
 * Persists the selected command, manifest path, build directory and custom arguments to the run
 * configuration XML via JDOM.
 */
class FlatpakRunConfiguration(
    project: Project,
    factory: FlatpakConfigurationFactory,
    name: String
) : RunConfigurationBase<Any>(project, factory, name) {

    /** The Flatpak command to execute when the configuration is run. */
    var command: FlatpakCommand = FlatpakCommand.BUILD

    /** Path to the Flatpak manifest file used by the command. */
    var manifestPath: String = FlatpakRunSettings.DEFAULT_MANIFEST

    /** Extra arguments appended to the generated command line. */
    var customArguments: List<String> = emptyList()

    /** Build directory used by flatpak-builder. */
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
        element.setAttribute(FlatpakRunAttributes.COMMAND.toString(), command.name)
        element.setAttribute(FlatpakRunAttributes.MANIFEST.toString(), manifestPath)
        element.setAttribute(FlatpakRunAttributes.CUSTOM_ARGS.toString(), customArguments.joinToString(";"))
        element.setAttribute(FlatpakRunAttributes.BUILD_DIR.toString(), buildDir)
    }

    /**
     * @return the settings editor used to edit this configuration
     */
    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = FlatpakRunSettingsEditor()

    /**
     * Read external values from IDE
     */
    override fun readExternal(element: Element) {
        super.readExternal(element)

        command = FlatpakCommand.valueOf(
            getAttributeValue(element,
                FlatpakRunAttributes.COMMAND,
                FlatpakCommand.BUILD.toString()
            )
        )

        manifestPath = getAttributeValue(element,
            FlatpakRunAttributes.MANIFEST,
            FlatpakRunSettings.DEFAULT_MANIFEST
        )

        customArguments = getAttributeValue(element,
            FlatpakRunAttributes.CUSTOM_ARGS,
            "")
            .split(";")
            .filter { it.isNotBlank() }

        buildDir = getAttributeValue(element,
            FlatpakRunAttributes.BUILD_DIR,
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