package io.github.andrepg.flatpak.runs.configuration

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.execution.FlatpakRunner
import io.github.andrepg.flatpak.runs.execution.RunConfigurationValidator
import io.github.andrepg.flatpak.runs.ui.FlatpakRunSettingsPanel
import io.github.andrepg.flatpak.utils.FlatpakManifestVfsReader

class FlatpakRunSettings(
    project: Project,
    factory: ConfigurationFactory?,
    name: String?,
) : RunConfigurationBase<FlatpakRunSettingsAttributes>(project, factory, name),
    LocatableConfiguration {
    /** The typed options holder for this configuration. */
    private val flatpakState: FlatpakRunSettingsAttributes
        get() = checkNotNull(state)

    /** The user-visible Flatpak command to execute when the configuration is run. */
    var command: UserVisibleCommand
        get() = UserVisibleCommand.valueOf(flatpakState.command ?: UserVisibleCommand.BUILD.name)
        set(value) {
            flatpakState.command = value.name
        }

    /** Path to the Flatpak manifest file used by the command. */
    var manifestPath: String
        get() = flatpakState.flatpakManifest ?: "flatpak.json"
        set(value) {
            flatpakState.flatpakManifest = value
        }

    /** Build directory used by flatpak-builder. */
    var buildDir: String
        get() = flatpakState.buildDir ?: "_build"
        set(value) {
            flatpakState.buildDir = value
        }

    /** Extra arguments appended to the generated command line. */
    var customArguments: List<String>
        get() =
            flatpakState.customArguments
                .orEmpty()
                .split(" ")
                .filter { it.isNotBlank() }
        set(value) {
            flatpakState.customArguments = value.joinToString(" ")
        }

    /** Whether to clean the build directory before build. */
    var enableForceClean: Boolean
        get() = flatpakState.enableForceClean
        set(value) {
            flatpakState.enableForceClean = value
        }

    /** Whether to deep clean (including flatpak-builder cache) before build. */
    var enableDeepClean: Boolean
        get() = flatpakState.enableDeepClean
        set(value) {
            flatpakState.enableDeepClean = value
        }

    /** Whether to enable GNOME portals. */
    var enablePortals: Boolean
        get() = flatpakState.enablePortals
        set(value) {
            flatpakState.enablePortals = value
        }

    /** Whether to enable theme and icon access. */
    var enableThemes: Boolean
        get() = flatpakState.enableThemes
        set(value) {
            flatpakState.enableThemes = value
        }

    /** Whether to enable audio/pulseaudio. */
    var enableAudio: Boolean
        get() = flatpakState.enableAudio
        set(value) {
            flatpakState.enableAudio = value
        }

    /** Whether to enable the Wayland socket. */
    var enableWayland: Boolean
        get() = flatpakState.enableWayland
        set(value) {
            flatpakState.enableWayland = value
        }

    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment,
    ): RunProfileState = FlatpakRunner(environment, this)

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = FlatpakRunSettingsPanel()

    /**
     * Validates the configuration before launching, collecting every problem
     * into a single error. Called by the run infrastructure before the state is
     * started.
     */
    override fun checkConfiguration() {
        val errors = RunConfigurationValidator.validate(this)
        if (errors.isNotEmpty()) {
            throw RuntimeConfigurationError(errors.joinToString("\n"))
        }
    }

    /**
     * Whether the configuration name is still the suggested `[command] <app-id>`
     * template rather than a user-chosen name. The IDE uses this to decide when
     * it may keep rewriting the name as the command/manifest change.
     */
    override fun isGeneratedName(): Boolean = GENERATED_NAME_PATTERN.matches(name)

    /**
     * The suggested name for the Run/Debug Configurations dialog and the
     * manifest right-click action: `[command] <app-id>` (e.g. `[build] org.example.App`).
     * Returns null until a manifest is configured, so the dialog falls back to
     * its default empty name.
     */
    override fun suggestedName(): String? {
        val manifest = flatpakState.flatpakManifest ?: return null
        val appId =
            FlatpakManifestVfsReader.readAppId(project, manifest)
                ?: manifest.substringAfterLast('/').substringBeforeLast('.')
        return FlatpakRunGenerator.formatRunName(command, appId)
    }

    private companion object {
        val GENERATED_NAME_PATTERN = Regex("""^\[[a-z]+] .+$""")
    }
}
