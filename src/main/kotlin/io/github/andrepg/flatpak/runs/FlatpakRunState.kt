package io.github.andrepg.flatpak.runs

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleViewContentType

/**
 * Run state that executes the configured Flatpak command.
 *
 * Implements [CommandLineState] to compose the process command line and attach the console
 * output view while the command runs.
 */
class FlatpakRunState(
    environment: ExecutionEnvironment,
    private val config: FlatpakRunConfiguration
) : CommandLineState(environment) {
    /** Builds the effective Flatpak command line from the run configuration. */
    val executor: FlatpakRunExecutor = FlatpakRunExecutor()

    /**
     * Composes the Flatpak command line and starts the underlying OS process.
     *
     * @return the handler managing the started process
     */
    override fun startProcess(): OSProcessHandler {
        val cmd = executor.commandLine(
            config.command,
            config.manifestPath,
            config.buildDir
        )

        val commandLine = composeCommandLine(cmd, config.customArguments)
        val handler = OSProcessHandler(commandLine)

        attachOutputConsole(handler)

        return handler
    }

    private fun attachOutputConsole(handler: OSProcessHandler) {
        val console = TextConsoleBuilderFactory.getInstance()
            .createBuilder(environment.project)
            .console

        console.print(handler.commandLine, ConsoleViewContentType.NORMAL_OUTPUT)
        console.attachToProcess(handler)
    }

    /**
     * Wraps the given command in a [GeneralCommandLine] that inherits the console parent
     * environment and uses the project base directory as its working directory.
     *
     * @param cmd the command tokens returned by the executor
     * @param customArgs extra arguments appended after the base command
     * @return the configured [GeneralCommandLine]
     */
    fun composeCommandLine(
        cmd: List<String>,
        customArgs: List<String> = emptyList()
    ) = GeneralCommandLine(cmd + customArgs)
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withWorkDirectory(environment.project.basePath)
}
