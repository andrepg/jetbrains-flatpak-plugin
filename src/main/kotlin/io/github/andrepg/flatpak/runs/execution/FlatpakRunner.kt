package io.github.andrepg.flatpak.runs.execution

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleViewContentType
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.shared.log.Log
import java.io.File

/**
 * Run state that executes the configured Flatpak command.
 *
 * Implements [CommandLineState] to compose the process command line and attach the console
 * output view while the command runs.
 */
class FlatpakRunner(
    environment: ExecutionEnvironment,
    private val config: FlatpakRunSettings
) : CommandLineState(environment) {
    private val log = Log.getInstance(FlatpakRunner::class.java)

    /**
     * Composes the Flatpak command line and starts the underlying OS process.
     *
     * Cleanup pre-steps (CLEAN/DEEP_CLEAN) run as their own processes in the
     * background via [CleanupThenProcessHandler], never on the EDT, so they are
     * never flattened into a single command line.
     *
     * @return the handler managing the started process
     */
    override fun startProcess(): ProcessHandler {
        log.info(
            "Flatpak run started: command=${config.command}, manifest=${config.manifestPath}, " +
                "buildDir=${config.buildDir}, forceClean=${config.enableForceClean}, " +
                "deepClean=${config.enableDeepClean}, portals=${config.enablePortals}, " +
                "themes=${config.enableThemes}, audio=${config.enableAudio}, wayland=${config.enableWayland}"
        )

        val plan = CommandSelectionStrategy().plan(config)
        val engine = CommandExecutionEngine(environment.project)

        val cleanupCommandLines = plan.preSteps.map { engine.buildCommand(it, config) }
        val mainCommandLine = engine.buildCommand(plan.main, config)

        val handler = if (cleanupCommandLines.isEmpty()) {
            engine.executeCommandSequence(mainCommandLine)
        } else {
            CleanupThenProcessHandler(
                cleanupCommandLines = cleanupCommandLines,
                mainCommandLine = engine.toGeneralCommandLine(mainCommandLine),
                workDir = environment.project.basePath?.let(::File),
            )
        }

        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                log.info("Flatpak command terminated with exit code ${event.exitCode}")
            }
        })

        attachOutputConsole(handler, mainCommandLine)

        handler.startNotify()

        return handler
    }

    private fun attachOutputConsole(handler: ProcessHandler, printedCommandLine: List<String>) {
        val console = TextConsoleBuilderFactory.getInstance()
            .createBuilder(environment.project)
            .console

        console.print(printedCommandLine.joinToString(" "), ConsoleViewContentType.NORMAL_OUTPUT)
        console.attachToProcess(handler)
    }
}
