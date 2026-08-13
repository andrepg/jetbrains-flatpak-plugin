package io.github.andrepg.flatpak.runs

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleViewContentType

class FlatpakRunState(
    environment: ExecutionEnvironment,
    private val config: FlatpakRunConfiguration
) : CommandLineState(environment) {
    val executor: FlatpakExecutor = FlatpakExecutor()

    override fun startProcess(): OSProcessHandler {
        val cmd = executor.commandLine(
            config.command,
            config.manifestPath,
            config.buildDir
        )

        val commandLine = composeCommandLine(cmd)
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

    fun composeCommandLine(cmd: List<String>) = GeneralCommandLine(cmd)
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withWorkDirectory(environment.project.basePath)
}
