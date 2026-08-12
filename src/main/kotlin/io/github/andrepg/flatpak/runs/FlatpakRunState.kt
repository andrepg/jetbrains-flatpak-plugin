package io.github.andrepg.flatpak.runs

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment

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

        val commandLine = GeneralCommandLine(cmd)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withWorkDirectory(environment.project.basePath)

        val handler = OSProcessHandler(commandLine)

        handler.startNotify()

        TextConsoleBuilderFactory.getInstance()
            .createBuilder(environment.project)
            .console
            .attachToProcess(handler)

        return handler
    }

    fun callFlatpakBinary() {
        // This method can be used to directly call the flatpak binary
        // Implementation can be added if needed for specific use cases
    }
}
