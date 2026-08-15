package io.github.andrepg.flatpak.runs.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.exception.FlatpakExecutionException
import io.github.andrepg.flatpak.exception.FlatpakPluginException
import io.github.andrepg.flatpak.runs.InternalCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.execution.commands.*
import io.github.andrepg.shared.log.Log

/**
 * Builds and executes Flatpak commands.
 *
 * Each [InternalCommand] is rendered as an independent command line; the runner
 * decides whether a command runs as a synchronous pre-step (cleanup) or as the
 * streamed main process.
 */
class CommandExecutionEngine(
    private val project: Project,
    private val hostBusAvailable: () -> Boolean = CommandExecutionArguments::hostHasFlatpakBus,
) {
    private val log = Log.getInstance(CommandExecutionEngine::class.java)

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
        val commandLine = try {
            when (command) {
                InternalCommand.CUSTOM -> CustomCommandFactory().create(config)
                InternalCommand.BUILD -> BuildCommandFactory().create(config)
                InternalCommand.RUN -> RunCommandFactory(hostBusAvailable).create(config)
                InternalCommand.EXPORT -> ExportBundleCommandFactory().create(config)
                InternalCommand.VALIDATE -> ValidateManifestCommandFactory().create(config)
            }
        } catch (e: FlatpakPluginException) {
            throw e
        } catch (e: Exception) {
            throw FlatpakExecutionException(
                "Failed to build the ${command.name.lowercase()} command from the run configuration",
                e
            )
        }

        log.debug("Built $command command line: ${commandLine.joinToString(" ")}")

        return commandLine
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
     * Starts the process handler for the given command line.
     *
     * @param commandLine The command line to start
     * @return The process handler for the executed command
     */
    fun executeCommand(commandLine: GeneralCommandLine): OSProcessHandler {
        log.info("Executing command: ${commandLine.commandLineString}")
        return try {
            OSProcessHandler(commandLine)
        } catch (e: Exception) {
            throw FlatpakExecutionException(
                "Failed to start the flatpak process: ${commandLine.commandLineString}",
                e
            )
        }
    }
}
