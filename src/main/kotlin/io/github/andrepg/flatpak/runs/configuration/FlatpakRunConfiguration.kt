package io.github.andrepg.flatpak.runs.configuration

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.FlatpakCommand
import io.github.andrepg.flatpak.runs.execution.FlatpakRunState
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
        element.setAttribute(ATTR_COMMAND, command.name)
        element.setAttribute(ATTR_MANIFEST, manifestPath)
        element.setAttribute(ATTR_CUSTOM_ARGS, customArguments.joinToString(";"))
        element.setAttribute(ATTR_BUILD_DIR, buildDir)
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
            getAttributeValue(element, ATTR_COMMAND, FlatpakCommand.BUILD.toString())
        )

        manifestPath = getAttributeValue(element, ATTR_MANIFEST, FlatpakRunSettings.DEFAULT_MANIFEST)

        customArguments = getAttributeValue(element, ATTR_CUSTOM_ARGS, "")
            .split(";")
            .filter { it.isNotBlank() }

        buildDir = getAttributeValue(element, ATTR_BUILD_DIR, FlatpakRunSettings.DEFAULT_OUTPUT)
    }

    /**
     * Get current element attribute, or return a default value
     */
    private fun getAttributeValue(
        element: Element,
        attribute: String,
        default: String
    ): String = element.getAttributeValue(attribute, default)

    private companion object {
        const val ATTR_COMMAND = "COMMAND"
        const val ATTR_MANIFEST = "MANIFEST"
        const val ATTR_CUSTOM_ARGS = "CUSTOM_ARGS"
        const val ATTR_BUILD_DIR = "BUILD_DIR"
    }
}