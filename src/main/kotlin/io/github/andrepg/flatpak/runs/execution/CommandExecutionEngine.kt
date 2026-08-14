package io.github.andrepg.flatpak.runs.execution

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.InternalCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.settings.FlatpakSettings
import io.github.andrepg.flatpak.utils.FlatpakManifestReader
import io.github.andrepg.shared.log.Log
import java.io.File

/**
 * Builds and executes Flatpak commands.
 *
 * Each [InternalCommand] is rendered as an independent command line; the runner
 * decides whether a command runs as a synchronous pre-step (cleanup) or as the
 * streamed main process.
 */
class CommandExecutionEngine(private val project: Project) {
    private val log = Log.getInstance(CommandExecutionEngine::class.java)

    private val flatpakBinaryPath: String get() = FlatpakSettings.flatpakBinary
    private val flatpakBuilderCommand: String get() = FlatpakSettings.builderBinary

    /**
     * Builds the command line for a single [command] from the run configuration.
     *
     * @param command The internal command to build
     * @param config The run configuration
     * @return The command line (executable + arguments) as a list of strings
     */
    fun buildCommand(
        command: InternalCommand,
        config: FlatpakRunSettings
    ): List<String> {
        val commandLine = when (command) {
            InternalCommand.CUSTOM -> buildCustomCommand(config)
            InternalCommand.CLEAN -> buildCleanCommand(config)
            InternalCommand.DEEP_CLEAN -> buildDeepCleanCommand(config)
            InternalCommand.BUILD -> buildBuildCommand(config)
            InternalCommand.RUN -> buildRunCommand(config)
            InternalCommand.EXPORT -> buildExportCommand(config)
            InternalCommand.VALIDATE -> buildValidateCommand(config)
        }
        log.debug("Built $command command line: ${commandLine.joinToString(" ")}")
        return commandLine
    }

    private fun basicFlatpakBuilderCommand() = listOf(flatpakBinaryPath, "run", flatpakBuilderCommand)

    private fun buildCustomCommand(config: FlatpakRunSettings): List<String> {
        val extraArguments = listOf(config.buildDir, config.manifestPath).plus(config.customArguments)

        return basicFlatpakBuilderCommand().plus(extraArguments)
    }

    private fun buildCleanCommand(config: FlatpakRunSettings): List<String> = listOf("rm", "-rf", config.buildDir)

    private fun buildDeepCleanCommand(config: FlatpakRunSettings): List<String> {
        val args = mutableListOf("rm", "-rf", config.buildDir)
        flatpakBuilderCacheDir()?.let { args += it }
        return args
    }

    private fun flatpakBuilderCacheDir(): String? =
        System.getProperty("user.home")?.let { home ->
            File(home, ".cache/flatpak-builder").path
        }

    private fun buildBuildCommand(config: FlatpakRunSettings): List<String> = basicFlatpakBuilderCommand()
        .plus(
            listOf(
                when (config.enableForceClean) {
                    true -> "--force-clean"
                    false -> ""
                },
                config.buildDir,
                config.manifestPath
            )
        )

    private fun buildRunCommand(config: FlatpakRunSettings): List<String> {
        val runCommand = getRunCommand(config.manifestPath)
        val arguments = listOf("--run").plus(buildSandboxOptions(config))
        val parameters = listOf(config.buildDir, config.manifestPath, runCommand)

        return basicFlatpakBuilderCommand().plus(arguments).plus(parameters)
    }

    /**
     * Sandbox options injected into the Run command so the app sees the requested
     * GNOME/portal integration. flatpak-builder's `--run` mode accepts the flatpak
     * context options (`--socket`, `--talk-name`, `--filesystem`, `--device`, `--env`),
     * which must be placed before the `DIRECTORY MANIFEST COMMAND` positional args.
     */
    private fun buildSandboxOptions(config: FlatpakRunSettings): List<String> {
        val options = mutableListOf<String>()

        if (config.enablePortals) {
            options += "--talk-name=org.freedesktop.portal.*"
            options += "--device=dri"
            options += "--env=GTK_USE_PORTAL=1"
        }

        if (config.enableThemes) {
            options += "--filesystem=xdg-config/gtk-3.0:ro"
            options += "--filesystem=xdg-data/icons:ro"
            options += "--filesystem=xdg-data/themes:ro"
            options += "--filesystem=xdg-config/glib-2.0"
        }

        if (config.enableAudio) {
            options += "--socket=pulseaudio"
        }

        if (config.enableWayland) {
            options += "--socket=wayland"
        }

        return options
    }

    private fun buildExportCommand(config: FlatpakRunSettings): List<String> {
        val arguments = listOf("--repo=repo-build", "--force-clean")
        val parameters = listOf(config.buildDir, config.manifestPath)

        return basicFlatpakBuilderCommand().plus(arguments).plus(parameters)
    }

    private fun buildValidateCommand(config: FlatpakRunSettings): List<String> =
        basicFlatpakBuilderCommand().plus(listOf("--show-manifest", config.manifestPath))

    /**
     * Resolves the program flatpak-builder must run inside the build sandbox:
     * the manifest's `command` field, falling back to the app-id.
     */
    private fun getRunCommand(manifestPath: String): String {
        FlatpakManifestReader.readCommand(manifestPath)?.let { return it }

        log.warn("Could not read command from manifest at $manifestPath; falling back to app-id")

        return getAppId(manifestPath)
    }

    private fun getAppId(manifestPath: String): String {
        FlatpakManifestReader.readAppId(manifestPath)?.let { return it }

        log.warn("Could not read app-id from manifest at $manifestPath; falling back to filename-derived app-id")

        return File(manifestPath).name.substringBeforeLast('.')
    }

    /**
     * Renders a command line into a [GeneralCommandLine] for the IDE process API.
     *
     * @param commandLine The command line (executable + arguments)
     * @return the general command line with the project as working directory
     */
    fun toGeneralCommandLine(commandLine: List<String>): GeneralCommandLine = GeneralCommandLine(commandLine)
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withWorkDirectory(project.basePath)

    /**
     * Starts the main process handler for the given command line.
     *
     * @param commandLine The command line to start
     * @return The process handler for the executed command
     */
    fun executeCommandSequence(
        commandLine: List<String>,
    ): OSProcessHandler {
        if (commandLine.isEmpty()) {
            throw ExecutionException("No command to execute: the configured command produced an empty command line")
        }

        log.info("Executing command sequence: ${commandLine.joinToString(" ")}")

        return OSProcessHandler(toGeneralCommandLine(commandLine))
    }
}
