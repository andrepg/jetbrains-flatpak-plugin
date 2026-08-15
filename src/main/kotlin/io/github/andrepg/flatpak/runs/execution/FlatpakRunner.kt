package io.github.andrepg.flatpak.runs.execution

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.shared.log.Log

/**
 * Run state that executes the configured Flatpak command.
 *
 * Implements [CommandLineState] to compose the process command line and attach the console
 * output view while the command runs. When the deep clean flag is enabled for the BUILD
 * command, the VFS deep clean runs first as a pre-step of [CommandChainProcessHandler].
 */
class FlatpakRunner(
    environment: ExecutionEnvironment,
    private val config: FlatpakRunSettings
) : CommandLineState(environment) {
    private val log = Log.getInstance(FlatpakRunner::class.java)

    private val engine = CommandExecutionEngine(environment.project)
    private val strategy = CommandExecutionStrategy().mapUserCommandToInternal(config.command)

    /**
     * Composes the Flatpak command line and starts the underlying process chain.
     *
     * @return the handler managing the process chain
     */
    override fun startProcess(): ProcessHandler {
        log.info(
            "Flatpak run started: command=${config.command}, manifest=${config.manifestPath}, " +
                    "buildDir=${config.buildDir}, forceClean=${config.enableForceClean}, " +
                    "deepClean=${config.enableDeepClean}, portals=${config.enablePortals}, " +
                    "themes=${config.enableThemes}, audio=${config.enableAudio}, wayland=${config.enableWayland}"
        )

        val commandLine = engine.buildCommand(strategy, config)
        val generalCommandLine = engine.toGeneralCommandLine(commandLine)
            .withWorkDirectory(environment.project.basePath)

        val preSteps = if (config.enableDeepClean && config.command == UserVisibleCommand.BUILD) {
            listOf(
                CommandChainProcessHandler.PreStep("DEEP_CLEAN") {
                    DeepCleanExecutor().clean(environment.project, config)
                }
            )
        } else {
            emptyList()
        }

        return CommandChainProcessHandler(
            commandLines = listOf(generalCommandLine),
            commandLabels = listOf(config.command.name),
            engine = engine,
            preSteps = preSteps
        )
    }
}
