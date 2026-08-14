package io.github.andrepg.flatpak.runs.configuration

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.execution.FlatpakRunner
import io.github.andrepg.flatpak.runs.execution.RunConfigurationValidator
import io.github.andrepg.flatpak.runs.ui.FlatpakRunSettingsPanel

class FlatpakRunSettings(
    project: Project,
    factory: ConfigurationFactory?,
    name: String?
) : RunConfigurationBase<FlatpakRunSettingsAttributes>(project, factory, name) {

    /** The typed options holder for this configuration. */
    private val flatpakState: FlatpakRunSettingsAttributes
        get() = checkNotNull(state)

    /** The user-visible Flatpak command to execute when the configuration is run. */
    var command: UserVisibleCommand
        get() = UserVisibleCommand.valueOf(flatpakState.command ?: UserVisibleCommand.BUILD.name)
        set(value) { flatpakState.command = value.name }

    /** Path to the Flatpak manifest file used by the command. */
    var manifestPath: String
        get() = flatpakState.flatpakManifest ?: "flatpak.json"
        set(value) { flatpakState.flatpakManifest = value }

    /** Build directory used by flatpak-builder. */
    var buildDir: String
        get() = flatpakState.buildDir ?: "_build"
        set(value) { flatpakState.buildDir = value }

    /** Extra arguments appended to the generated command line. */
    var customArguments: List<String>
        get() = flatpakState.customArguments.orEmpty().split(" ").filter { it.isNotBlank() }
        set(value) { flatpakState.customArguments = value.joinToString(" ") }

    /** Whether to clean the build directory before build. */
    var enableForceClean: Boolean
        get() = flatpakState.enableForceClean
        set(value) { flatpakState.enableForceClean = value }

    /** Whether to deep clean (including flatpak-builder cache) before build. */
    var enableDeepClean: Boolean
        get() = flatpakState.enableDeepClean
        set(value) { flatpakState.enableDeepClean = value }

    /** Whether to enable GNOME portals. */
    var enablePortals: Boolean
        get() = flatpakState.enablePortals
        set(value) { flatpakState.enablePortals = value }

    /** Whether to enable theme and icon access. */
    var enableThemes: Boolean
        get() = flatpakState.enableThemes
        set(value) { flatpakState.enableThemes = value }

    /** Whether to enable audio/pulseaudio. */
    var enableAudio: Boolean
        get() = flatpakState.enableAudio
        set(value) { flatpakState.enableAudio = value }

    /** Whether to enable the Wayland socket. */
    var enableWayland: Boolean
        get() = flatpakState.enableWayland
        set(value) { flatpakState.enableWayland = value }

    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ): RunProfileState = FlatpakRunner(environment, this)

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        FlatpakRunSettingsPanel()

    /**
     * Validates the configuration before launching, collecting every problem
     * into a single error. Called by the run infrastructure before the state is
     * started.
     */
    override fun checkConfiguration() {
        super.checkConfiguration()
        val errors = RunConfigurationValidator.validate(this)
        if (errors.isNotEmpty()) {
            throw RuntimeConfigurationError(errors.joinToString("\n"))
        }
    }
}
