package io.github.andrepg.flatpak.runs.commands

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.exception.FlatpakExecutionException
import io.github.andrepg.flatpak.exception.FlatpakPluginException
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.settings.FlatpakSettings
import io.github.andrepg.flatpak.utils.FlatpakManifestVfsReader
import io.github.andrepg.shared.log.Log

/**
 * Builds and executes Flatpak commands.
 *
 * Each [UserVisibleCommand] is rendered as an independent command line (its own
 * builder below); the runner decides whether a command runs as a synchronous
 * pre-step (cleanup) or as the streamed main process.
 */
class CommandExecutionEngine(
    private val project: Project,
    private val hostBusAvailable: () -> Boolean = CommandExecutionArguments::hostHasFlatpakBus,
) {
    private val log = Log.getInstance(CommandExecutionEngine::class.java)

    /**
     * Builds the command line for a single [command] from the run configuration.
     *
     * @param command The command to build
     * @param config The run configuration
     * @return The command line (executable + arguments) as a list of strings
     */
    fun buildCommand(
        command: UserVisibleCommand,
        config: FlatpakRunSettings,
    ): List<String> {
        val commandLine =
            try {
                when (command) {
                    UserVisibleCommand.BUILD -> buildBuildCommand(config)
                    UserVisibleCommand.EXPORT -> buildExportCommand(config)
                    UserVisibleCommand.RUN -> buildRunCommand(config)
                    UserVisibleCommand.VALIDATE -> buildValidateCommand(config)
                    UserVisibleCommand.CUSTOM -> buildCustomCommand(config)
                }
            } catch (e: FlatpakPluginException) {
                throw e
            } catch (e: Exception) {
                throw FlatpakExecutionException(
                    "Failed to build the ${command.name.lowercase()} command from the run configuration",
                    e,
                )
            }

        log.debug("Built $command command line: ${commandLine.joinToString(" ")}")

        return commandLine
    }

    private fun buildBuildCommand(config: FlatpakRunSettings): List<String> =
        buildList {
            addAll(FlatpakCommandLine.flatpakBuilderCommand())
            if (config.enableForceClean && config.command == UserVisibleCommand.BUILD) {
                addAll(CommandExecutionArguments.FORCE_CLEAN)
            }
            add(config.effectiveBuildDir())
            add(config.effectiveManifestPath())
        }

    private fun buildExportCommand(config: FlatpakRunSettings): List<String> =
        FlatpakCommandLine.flatpakBuilderCommand() +
            listOf("--repo=repo-build", config.effectiveBuildDir(), config.effectiveManifestPath())

    private fun buildValidateCommand(config: FlatpakRunSettings): List<String> =
        FlatpakCommandLine.flatpakBuilderCommand() +
            listOf("--show-manifest", config.effectiveManifestPath())

    private fun buildCustomCommand(config: FlatpakRunSettings): List<String> =
        FlatpakCommandLine.flatpakBuilderCommand() +
            listOf(config.effectiveBuildDir(), config.effectiveManifestPath()) +
            config.customArguments

    /**
     * Runs the manifest's `command` (falling back to the app-id) inside the
     * builder sandbox. The opt-in D-Bus sockets are only added when the host
     * exposes the flatpak bus proxy; otherwise they are skipped with a warning
     * and the app gets the filtered default bus (GNOME Builder-style).
     */
    private fun buildRunCommand(config: FlatpakRunSettings): List<String> {
        val appCommand =
            FlatpakManifestVfsReader.readCommand(config.project, config.manifestPath)
                ?: FlatpakManifestVfsReader.readAppId(config.project, config.manifestPath)
        return FlatpakSandboxRunner.run(
            flatpakBinary = FlatpakSettings.flatpakBinary,
            appRef = FlatpakSettings.builderBinary,
            refArguments =
                buildList {
                    add("--run")
                    if (hostBusAvailable()) {
                        addAll(CommandExecutionArguments.DEFAULT_BUS)
                    } else {
                        log.warn("Host exposes no /run/flatpak/bus; skipping D-Bus sockets (app gets the filtered default bus)")
                    }
                    addAll(buildSandboxOptions(config))
                    add(config.effectiveBuildDir())
                    add(config.effectiveManifestPath())
                    if (appCommand != null) add(appCommand)
                },
        )
    }

    /**
     * Renders a command line into a [GeneralCommandLine] for the IDE process API.
     *
     * @param commandLine The command line (executable + arguments)
     * @return the general command line with the project as working directory
     */
    fun toGeneralCommandLine(commandLine: List<String>): GeneralCommandLine =
        GeneralCommandLine(commandLine)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withWorkDirectory(project.basePath)

    /**
     * Starts the process handler for the given command line.
     *
     * Recursive destruction is enabled so that stopping the run tears down the
     * whole flatpak process tree (bwrap, rofiles-fuse), not just the direct
     * child — an unclean teardown leaves dead FUSE mounts inside the build dir.
     *
     * @param commandLine The command line to start
     * @return The process handler for the executed command
     */
    fun executeCommand(commandLine: GeneralCommandLine): OSProcessHandler {
        log.info("Executing command: ${commandLine.commandLineString}")
        return try {
            OSProcessHandler(commandLine).apply { setShouldDestroyProcessRecursively(true) }
        } catch (e: Exception) {
            throw FlatpakExecutionException(
                "Failed to start the flatpak process: ${commandLine.commandLineString}",
                e,
            )
        }
    }
}
